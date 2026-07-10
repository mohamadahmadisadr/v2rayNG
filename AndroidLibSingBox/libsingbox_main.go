// Package libsingbox is a thin gomobile wrapper around sing-box, exposing a
// CoreController-style lifecycle API that mirrors AndroidLibXrayLite's libv2ray so the
// Android side (dev.sadr.atlas.core.engine.singbox.SingBoxCore) can drive it symmetrically
// with the xray engine.
//
// SCOPE (phase 1): start/stop a sing-box instance from a JSON config that the Kotlin
// SingBoxConfigBuilder produces (a local mixed SOCKS/HTTP inbound + one proxy outbound).
// The app already excludes its own UID from the VPN via addDisallowedApplication(self), so
// the proxy's outbound sockets bypass the tun automatically — no platform-interface /
// per-socket protect() wiring is needed here.
//
// Delay measurement is deliberately NOT implemented in Go: the Kotlin adapter measures by
// issuing an HTTP request through the local SOCKS inbound, which avoids depending on
// sing-box's internal (version-volatile) outbound-dial APIs.
//
// ─────────────────────────────────────────────────────────────────────────────────────
// VERSION NOTE: this targets sing-box v1.11.x. The context/registry construction below
// (box.Context + include.*Registry) and the JSON unmarshal helper changed across releases:
//   - 1.11: box.Context(ctx, inbound, outbound, endpoint)
//   - 1.12+: adds a DNS-transport registry argument
// If a build fails here, use cmd/sing-box/cmd_run.go of your pinned version as the
// reference for how to build the context and unmarshal option.Options.
// ─────────────────────────────────────────────────────────────────────────────────────
package libsingbox

import (
	"context"
	"fmt"
	"sync"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/common/json"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
)

// libVersion is bumped independently of sing-box for the Kotlin side to sanity-check the AAR.
const libVersion = 1

// CoreCallbackHandler receives lifecycle notifications. Method shapes mirror libv2ray's
// CoreCallbackHandler so the Kotlin adapter is symmetric with the xray one.
type CoreCallbackHandler interface {
	Startup() int
	Shutdown() int
	OnEmitStatus(status int, message string) int
}

// CoreController owns one sing-box instance.
type CoreController struct {
	CallbackHandler CoreCallbackHandler
	IsRunning       bool

	mutex    sync.Mutex
	instance *box.Box
	cancel   context.CancelFunc
}

// NewCoreController returns a stopped controller wired to the given callback.
func NewCoreController(handler CoreCallbackHandler) *CoreController {
	return &CoreController{CallbackHandler: handler}
}

// StartLoop parses configContent and starts the sing-box instance.
//
// tunFd is accepted for API symmetry with libv2ray but is unused in phase 1 (hev owns the
// TUN); it becomes relevant only when we move to sing-box's built-in tun inbound.
func (c *CoreController) StartLoop(configContent string, tunFd int32) error {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	if c.IsRunning {
		return nil
	}

	ctx, cancel := context.WithCancel(context.Background())

	// Build the protocol registries into the context (sing-box 1.11 style).
	ctx = box.Context(ctx, include.InboundRegistry(), include.OutboundRegistry(), include.EndpointRegistry())

	options, err := json.UnmarshalExtendedContext[option.Options](ctx, []byte(configContent))
	if err != nil {
		cancel()
		return fmt.Errorf("config parse error: %w", err)
	}

	instance, err := box.New(box.Options{Context: ctx, Options: options})
	if err != nil {
		cancel()
		return fmt.Errorf("instance creation failed: %w", err)
	}

	if err := instance.Start(); err != nil {
		cancel()
		_ = instance.Close()
		return fmt.Errorf("startup failed: %w", err)
	}

	c.instance = instance
	c.cancel = cancel
	c.IsRunning = true

	if c.CallbackHandler != nil {
		c.CallbackHandler.Startup()
		c.CallbackHandler.OnEmitStatus(0, "Started successfully, running")
	}
	return nil
}

// StopLoop shuts the instance down and releases resources. Safe when not running.
func (c *CoreController) StopLoop() error {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	if !c.IsRunning {
		return nil
	}

	var err error
	if c.instance != nil {
		err = c.instance.Close()
		c.instance = nil
	}
	if c.cancel != nil {
		c.cancel()
		c.cancel = nil
	}
	c.IsRunning = false

	if c.CallbackHandler != nil {
		c.CallbackHandler.OnEmitStatus(0, "Core stopped")
	}
	return err
}

// QueryAllOutboundTrafficStats returns per-outbound traffic stats in the same
// "tag,direction,value;..." encoding libv2ray uses, or "" when unavailable.
//
// TODO(phase-later): sing-box exposes traffic via experimental.v2ray_api (StatsService) or
// clash_api. To implement, enable that in the config and read counters here. Stubbed for
// phase 1 so the traffic notification simply shows nothing rather than crashing.
func (c *CoreController) QueryAllOutboundTrafficStats() string {
	return ""
}

// CheckVersion reports the wrapper and sing-box versions.
func CheckVersion() string {
	return fmt.Sprintf("LibSingBox v%d, sing-box %s", libVersion, box.Version())
}
