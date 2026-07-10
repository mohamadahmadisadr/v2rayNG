# AndroidLibSingBox

A thin [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile) wrapper around
[sing-box](https://github.com/SagerNet/sing-box), mirroring `AndroidLibXrayLite`/`libv2ray`.
It exposes a `CoreController`-style lifecycle so the app's engine seam
(`dev.sadr.atlas.core.engine.ProxyCore`) can drive sing-box the same way it drives xray.

## What it exposes

| Go | Purpose |
|----|---------|
| `NewCoreController(handler)` | create a stopped controller |
| `StartLoop(config, tunFd)` | parse the sing-box JSON and start the instance (`tunFd` unused in phase 1) |
| `StopLoop()` | stop and release |
| `IsRunning` | running state |
| `QueryAllOutboundTrafficStats()` | traffic stats (stubbed — see TODO in source) |
| `CheckVersion()` | wrapper + sing-box version |

**Delay measurement is intentionally not here** — the Kotlin `SingBoxCore` adapter measures by
making an HTTP request through the local SOCKS inbound, avoiding sing-box's version-volatile
internal dial APIs.

**No socket protection is wired** — the app excludes its own UID from the VPN
(`addDisallowedApplication(self)`), so the proxy's outbound sockets bypass the tun automatically.

## Build

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk      # or NDK_HOME
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
./compile-singbox.sh
```

Produces `libsingbox.aar`.

## Integrate into the app

The app auto-includes any AAR in `app/libs/` (`fileTree("libs", include "*.aar")` in
`app/build.gradle.kts`). So:

```bash
cp libsingbox.aar ../V2rayNG/app/libs/
```

The generated Java package is `libsingbox` (from `-javapkg`), so Kotlin imports look like
`import libsingbox.CoreController`. The upcoming `SingBoxCore`/`SingBoxCoreEngine` adapter will
wrap these exactly as `engine/xray/XrayCore` wraps `libv2ray`.

## Version pinning ⚠️

`go.mod` pins a **sing-box version**, and the config schema emitted by
`SingBoxConfigBuilder` (Kotlin) must match it. This wrapper targets **sing-box v1.11.x**.
Across versions the context/registry setup in `libsingbox_main.go` changes:

- **1.11**: `box.Context(ctx, include.InboundRegistry(), include.OutboundRegistry(), include.EndpointRegistry())`
- **1.12+**: adds a DNS-transport registry argument

If a build fails in `StartLoop`, use `cmd/sing-box/cmd_run.go` of your pinned version as the
reference for building the context and unmarshalling `option.Options`.

## Status

Phase 1 only: lifecycle + start from a minimal config (mixed SOCKS/HTTP inbound + proxy
outbound). Not yet implemented: traffic stats, and sing-box's built-in tun inbound (phase 2,
which drops hev + the SOCKS loopback hop).
