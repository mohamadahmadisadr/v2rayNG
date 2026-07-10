#!/bin/bash
set -o errexit -o pipefail -o nounset
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$__dir"
: "${ANDROID_NDK_HOME:=${NDK_HOME:-}}"
[[ -d "${ANDROID_NDK_HOME}" ]] || { echo "set ANDROID_NDK_HOME"; exit 1; }
export ANDROID_NDK_HOME
go mod tidy
gomobile init || true
gomobile bind \
  -target=android/arm64,android/arm \
  -androidapi 24 -trimpath -ldflags="-s -w -checklinkname=0" \
  -o "$__dir/libcore.aar" \
  github.com/2dust/AndroidLibXrayLite \
  github.com/mohamadahmadisadr/AndroidLibSingBox
echo "Built: $__dir/libcore.aar"
