#!/usr/bin/env bash
# Build libobadh_jni.so for each Android ABI into app/src/main/jniLibs.
# Needs: rustup (toolchain pinned in rust/obadh-jni/rust-toolchain.toml),
# cargo-ndk, and an Android NDK (ANDROID_NDK_HOME or $ANDROID_HOME/ndk/<ver>).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  ANDROID_NDK_HOME="$(ls -d "${ANDROID_HOME:?set ANDROID_HOME}"/ndk/* | sort -V | tail -1)"
  export ANDROID_NDK_HOME
fi
cd "$here/rust/obadh-jni"
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o "$here/app/src/main/jniLibs" build --release
# cargo-ndk also copies the engine's own cdylib (obadh_engine has crate-type cdylib). Our JNI
# library links the engine statically, so that copy is dead weight in every ABI of the APK.
find "$here/app/src/main/jniLibs" -name 'libobadh_engine-*.so' -delete
echo "built into $here/app/src/main/jniLibs"
