# Building, installing, releasing

## Setup

You need:

- **JDK 17 or 21.** Android Studio's bundled JDK works
  (`/Applications/Android Studio.app/Contents/jbr/Contents/Home` on macOS).
- **The Android SDK**, with platform 35 and an **NDK**. `ANDROID_HOME` should point
  at the SDK, or put `sdk.dir=...` in `local.properties` (git-ignored).
- **rustup.** The toolchain (Rust 1.89.0 with the three Android targets) is pinned
  in `rust/obadh-jni/rust-toolchain.toml` and installs itself on first use.
- **cargo-ndk** (`cargo install cargo-ndk`).
- **The obadh-ios repo checked out next to this one**, because the models are
  synced from `../obadh-ios/Resources/ObadhModels` (override with
  `OBADH_MODELS_SRC`).

```bash
./scripts/bootstrap.sh      # sync models, then build the native library
./gradlew :app:assembleDebug
```

The Gradle wrapper is committed. `app/build.gradle.kts` stamps the build with the
git revision (`-dirty` for an uncommitted tree) and a UTC time, shown in
Settings › Version, so you can tell what is actually installed.

## The Rust bridge

```bash
./scripts/build-rust-android.sh   # arm64-v8a, armeabi-v7a, x86_64 into app/src/main/jniLibs
```

Rerun after any change under `rust/`. `jniLibs/` is git-ignored. The script
removes the engine's own `libobadh_engine-*.so` that `cargo-ndk` also emits: our
library links the engine statically, so that copy is dead weight in every ABI.

Adding an engine call means: add the function to `rust/obadh-jni/src/lib.rs`
(one `Java_org_unmukto_obadh_engine_ObadhNative_*` function that marshals bytes
into one `cabi` call), declare it `external` in `ObadhNative.kt`, wrap it in
`ObadhBridgeClient`, and rebuild the library.

## Models

```bash
./scripts/sync-models.sh
```

Copies the autocorrect FSTs, the autosuggest n-gram and the three emoji
artifacts from obadh-ios into `app/src/main/assets/ObadhModels` (git-ignored), so
both platforms ship byte-identical data. At runtime the first launch copies them
to `filesDir/models` (versioned by app version and code) because the engine opens
paths and APK assets are not files. The `.fst` and `.bin` extensions are stored
uncompressed in the APK.

## Engine version bumps

1. Branch.
2. Bump `obadh_engine` in `rust/obadh-jni/Cargo.toml`.
3. Make sure obadh-ios has the matching data artifacts, then `./scripts/bootstrap.sh`.
4. `./gradlew :app:testDebugUnitTest`, then install and check **Settings ›
   Version** shows the new engine version.
5. Commit.

There is no fingerprint pin on Android yet, so step 4 does not catch a silent
artifact swap. See [KNOWN-ISSUES.md](../KNOWN-ISSUES.md#ki-005).

## Device install

```bash
./gradlew :app:installDebug
```

or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

### Wireless debugging

On the phone: **Developer options › Wireless debugging › Pair device with pairing
code**. Then:

```bash
adb pair <ip>:<pairing-port>        # enter the six-digit code
adb devices -l                      # the phone appears as an mDNS service
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

The connect port differs from the pairing port and changes when wireless
debugging is toggled. Some vendors (Xiaomi) refuse injected input and
`adb shell ime` unless extra developer options are on; see
[testing.md](testing.md#device-notes). Android requires the user to turn a new
keyboard on and select it themselves, so the app's setup flow is the way in.

## Build types

| Type | Purpose |
|---|---|
| `debug` | Development. Includes `KeyboardPreviewActivity` and honours the `screen` and `step` launch extras. |
| `release` | R8-minified, with a keep rule for the JNI entry class. It **builds** (`./gradlew :app:assembleRelease`, unsigned, about 43 MB), but it has not been installed or run, and no signing config exists yet. |

## Releasing

Not configured yet: there is no signing config, no version-bump script, and no
store listing. When it is, the checklist is: bump `versionName` and `versionCode`
in `app/build.gradle.kts`, run the unit tests, build a signed release, **install
and run the release build** (R8 can strip JNI-reachable code), verify Settings ›
Version, and record the change in [CHANGELOG.md](../CHANGELOG.md).
