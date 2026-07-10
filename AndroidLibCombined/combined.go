//go:build android

// Package combined exists only to keep both wrapper modules in the dependency graph for
// `go mod tidy`. It is never bound; gomobile binds the two wrapper packages explicitly.
package combined

import (
	_ "github.com/2dust/AndroidLibXrayLite"
	_ "github.com/mohamadahmadisadr/AndroidLibSingBox"
)
