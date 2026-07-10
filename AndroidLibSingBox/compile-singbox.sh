#!/bin/bash
# Builds libsingbox.aar via gomobile, mirroring how AndroidLibXrayLite produces libv2ray.aar.
#
# Requirements (install once):
#   - Go 1.23+            https://go.dev/dl/
#   - Android NDK, with $ANDROID_NDK_HOME (or $NDK_HOME) set
#   - gomobile:  go install golang.org/x/mobile/cmd/gomobile@latest
#                go install golang.org/x/mobile/cmd/gobind@latest
#
# Output: ./libsingbox.aar  (copy into the app, see README.md)
set -o errexit
set -o pipefail
set -o nounset

__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$__dir"

: "${ANDROID_NDK_HOME:=${NDK_HOME:-}}"
if [[ -z "${ANDROID_NDK_HOME}" || ! -d "${ANDROID_NDK_HOME}" ]]; then
  echo "Android NDK not found. Set \$ANDROID_NDK_HOME (or \$NDK_HOME)."
  exit 1
fi
export ANDROID_NDK_HOME

# Resolve the (large) sing-box dependency tree and fill go.sum.
go mod tidy

# gomobile needs to be initialised once per environment; harmless to re-run.
gomobile init || true

# Match the ABIs the app ships. Add x86/x86_64 if you build for emulators of those ABIs.
gomobile bind \
  -target=android/arm64,android/arm \
  -androidapi 24 \
  -javapkg=libsingbox \
  -trimpath \
  -ldflags="-s -w" \
  -o "$__dir/libsingbox.aar" \
  .

echo "Built: $__dir/libsingbox.aar"
