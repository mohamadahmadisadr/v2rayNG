module github.com/mohamadahmadisadr/AndroidLibSingBox

// sing-box 1.11 needs a recent Go toolchain.
go 1.23

// NOTE: pin the sing-box version you intend to ship; the config schema the Kotlin
// SingBoxConfigBuilder emits must match it. After editing, run `go mod tidy` to resolve
// the (large) transitive dependency set and fill go.sum.
require (
	github.com/sagernet/sing-box v1.11.13
	golang.org/x/mobile v0.0.0-20240806205939-81131f6468ab
)
