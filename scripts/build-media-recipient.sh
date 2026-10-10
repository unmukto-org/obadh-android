#!/usr/bin/env bash
# Tiny independent Android protocol recipient, using only the already-installed SDK/JDK.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
source "$root/scripts/dev-env.sh"
build="$root/build/media-recipient"
tools="$ANDROID_HOME/build-tools/36.0.0"
android="$ANDROID_HOME/platforms/android-37.0/android.jar"
rm -rf "$build/classes" "$build/dex"
mkdir -p "$build/classes" "$build/dex"
javac -source 8 -target 8 -classpath "$android" -d "$build/classes" "$root/scripts/media-recipient/MediaRecipientActivity.java"
"$tools/aapt2" link -I "$android" --manifest "$root/scripts/media-recipient/AndroidManifest.xml" -o "$build/unsigned.apk"
"$tools/d8" --lib "$android" --min-api 28 --output "$build/dex" "$build/classes/org/unmukto/obadh/mediarecipient/"*.class
python3 - "$build" <<'PY'
import pathlib,sys,zipfile
root=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(root/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as apk:
    for dex in (root/'dex').glob('*.dex'):apk.write(dex,dex.name)
PY
"$tools/zipalign" -f 4 "$build/unsigned.apk" "$build/aligned.apk"
"$tools/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$build/media-recipient.apk" "$build/aligned.apk"
rm -f "$build/unsigned.apk" "$build/aligned.apk"
echo "Built independent test recipient: $build/media-recipient.apk"
