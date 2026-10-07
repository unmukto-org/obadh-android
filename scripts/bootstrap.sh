#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
command -v cargo >/dev/null || { echo "install rustup first: https://rustup.rs"; exit 1; }
command -v cargo-ndk >/dev/null || cargo install cargo-ndk
"$here/sync-models.sh"
"$here/build-rust-android.sh"
