#!/usr/bin/env bash
# Build the shipped ARM64 bridge; optional ABI arguments are explicit to avoid unused artifacts.
# Needs: rustup (toolchain pinned in rust/obadh-jni/rust-toolchain.toml),
# cargo-ndk, and an Android NDK (ANDROID_NDK_HOME or $ANDROID_HOME/ndk/<ver>).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  ANDROID_NDK_HOME="$(ls -d "${ANDROID_HOME:?set ANDROID_HOME}"/ndk/* | sort -V | tail -1)"
  export ANDROID_NDK_HOME
fi
cd "$here/rust/obadh-jni"
target_args=()
if [ "$#" -eq 0 ]; then set -- arm64-v8a; fi
for abi in "$@"; do
  case "$abi" in
    arm64-v8a|armeabi-v7a|x86_64) target_args+=(-t "$abi") ;;
    *) echo "unsupported ABI: $abi" >&2; exit 2 ;;
  esac
done
# Both LOAD and RELRO boundaries must work on current 16 KB Android kernels.
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
cargo ndk "${target_args[@]}" -o "$here/app/src/main/jniLibs" build --release
# cargo-ndk also copies the engine's own cdylib (obadh_engine has crate-type cdylib). Our JNI
# library links the engine statically, so that copy is dead weight in every ABI of the APK.
find "$here/app/src/main/jniLibs" -name 'libobadh_engine-*.so' -delete
echo "built into $here/app/src/main/jniLibs"
