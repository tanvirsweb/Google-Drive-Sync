#!/usr/bin/env bash
# Cross-compiles rclone for Android and drops it into app/src/main/jniLibs/<abi>/librclone.so.
#
# The binary is packaged as a "native library" so Android extracts it into the app's
# nativeLibraryDir, the only app-writable location that is allowed to hold executables
# on Android 10+. It is built with cgo against the NDK so that DNS lookups go through
# bionic (pure-Go builds can't resolve hosts on Android: there is no /etc/resolv.conf).
#
# It builds rclone-android/, a trimmed rclone main that includes only the Google Drive and
# local backends and the commands the app runs. The rclone version is pinned in
# rclone-android/go.mod (go.sum locks every dependency); to upgrade, run there:
#   go get github.com/rclone/rclone@vX.Y.Z && go mod tidy
#
# Requirements: Go (>= the version in rclone-android/go.mod) and the Android NDK.
# Usage: scripts/build-rclone.sh [abi ...]      (default: arm64-v8a armeabi-v7a x86_64)
# Env:   ANDROID_NDK_HOME, ANDROID_HOME, FORCE=1 to rebuild.
set -euo pipefail

API=24
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="$ROOT/rclone-android"
RCLONE_VERSION="$(sed -n 's/^require github.com\/rclone\/rclone \(v[^ ]*\).*/\1/p' "$MODULE/go.mod")"
[ -n "$RCLONE_VERSION" ] || { echo "Could not read the rclone version from $MODULE/go.mod" >&2; exit 1; }
# Rebuild when the version, the included backends/commands, or any dependency changes.
sha256() { if command -v sha256sum >/dev/null; then sha256sum; else shasum -a 256; fi; }
STAMP="$RCLONE_VERSION-$(cat "$MODULE/main.go" "$MODULE/go.mod" "$MODULE/go.sum" | sha256 | cut -c1-16)"
OUT="$ROOT/app/src/main/jniLibs"
STAMPS="$ROOT/app/.rclone"   # build stamps (kept out of jniLibs so they are not packaged)
ABIS=("$@")
[ ${#ABIS[@]} -eq 0 ] && ABIS=(arm64-v8a armeabi-v7a x86_64)

is_current() { [ -f "$OUT/$1/librclone.so" ] && [ "$(cat "$STAMPS/$1.version" 2>/dev/null)" = "$STAMP" ]; }

# Nothing to do (and no Go/NDK needed) when every binary matches the current sources.
if [ -z "${FORCE:-}" ]; then
  all_current=1
  for abi in "${ABIS[@]}"; do is_current "$abi" || all_current=0; done
  if [ "$all_current" = 1 ]; then
    echo "rclone $RCLONE_VERSION is up to date for: ${ABIS[*]}"
    exit 0
  fi
fi

find_ndk() {
  if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then echo "$ANDROID_NDK_HOME"; return; fi
  local sdks=()
  if [ -f "$ROOT/local.properties" ]; then
    sdks+=("$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties")")
  fi
  sdks+=("${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk")
  local sdk ndk
  for sdk in "${sdks[@]}"; do
    [ -n "$sdk" ] && [ -d "$sdk/ndk" ] || continue
    ndk="$(find "$sdk/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
    if [ -n "$ndk" ]; then echo "$ndk"; return; fi
  done
}

NDK="$(find_ndk || true)"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
  echo "Android NDK not found. Install it (SDK Manager > SDK Tools > NDK) or set ANDROID_NDK_HOME." >&2
  exit 1
fi
command -v go >/dev/null || { echo "Go toolchain not found in PATH." >&2; exit 1; }

case "$(uname -s)" in
  Linux)  HOST=linux-x86_64 ;;
  Darwin) HOST=darwin-x86_64 ;;
  *) echo "Unsupported build host $(uname -s)" >&2; exit 1 ;;
esac
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"

echo "Fetching rclone $RCLONE_VERSION dependencies…"
(cd "$MODULE" && GOFLAGS=-modcacherw go mod download)

for abi in "${ABIS[@]}"; do
  case "$abi" in
    arm64-v8a)   GOARCH=arm64; GOARM=;  CC_TRIPLE=aarch64-linux-android ;;
    armeabi-v7a) GOARCH=arm;   GOARM=7; CC_TRIPLE=armv7a-linux-androideabi ;;
    x86_64)      GOARCH=amd64; GOARM=;  CC_TRIPLE=x86_64-linux-android ;;
    x86)         GOARCH=386;   GOARM=;  CC_TRIPLE=i686-linux-android ;;
    *) echo "Unknown ABI $abi" >&2; exit 1 ;;
  esac
  dest="$OUT/$abi/librclone.so"
  if [ -z "${FORCE:-}" ] && is_current "$abi"; then
    echo "[$abi] rclone $RCLONE_VERSION already built, skipping (FORCE=1 to rebuild)"
    continue
  fi
  echo "[$abi] building rclone $RCLONE_VERSION…"
  mkdir -p "$OUT/$abi"
  (
    cd "$MODULE"
    CGO_ENABLED=1 GOOS=android GOARCH="$GOARCH" GOARM="$GOARM" \
    CC="$TOOLCHAIN/${CC_TRIPLE}${API}-clang" CXX="$TOOLCHAIN/${CC_TRIPLE}${API}-clang++" \
    GOFLAGS="-modcacherw -trimpath" \
      go build \
        -buildvcs=false \
        -ldflags "-s -w -buildid= -X github.com/rclone/rclone/fs.Version=$RCLONE_VERSION" \
        -o "$dest" .
  )
  mkdir -p "$STAMPS"
  echo "$STAMP" > "$STAMPS/$abi.version"
  echo "[$abi] -> $dest ($(du -h "$dest" | cut -f1))"
done
