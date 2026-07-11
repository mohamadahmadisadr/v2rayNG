#!/bin/bash
set -o errexit -o pipefail -o nounset
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$__dir"
: "${ANDROID_NDK_HOME:=${NDK_HOME:-}}"
[[ -d "${ANDROID_NDK_HOME}" ]] || { echo "set ANDROID_NDK_HOME"; exit 1; }
export ANDROID_NDK_HOME
go mod tidy
gomobile init || true
# sing-box gates features behind build tags; enable the ones the app needs
# (REALITY/uTLS, QUIC for hysteria2/tuic, gRPC, gVisor tun for phase 2, WireGuard,
# clash_api for traffic stats). xray-core ignores these tags.
SINGBOX_TAGS="with_gvisor,with_quic,with_utls,with_grpc,with_wireguard,with_clash_api"

gomobile bind \
  -target=android/arm64,android/arm \
  -androidapi 24 -trimpath -ldflags="-s -w -checklinkname=0" \
  -tags "$SINGBOX_TAGS" \
  -o "$__dir/libcore.aar" \
  github.com/2dust/AndroidLibXrayLite \
  github.com/mohamadahmadisadr/AndroidLibSingBox
echo "Built: $__dir/libcore.aar"
