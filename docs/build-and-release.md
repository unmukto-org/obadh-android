# Building, installing, releasing

## Setup

### Local command-line development on macOS

```bash
source scripts/dev-env.sh         # existing JDK 17 and ~/Library/Android/sdk
./scripts/bootstrap.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
emulator -avd obadh-api35 -no-snapshot -no-boot-anim
# In another shell:
source scripts/dev-env.sh
./gradlew :app:installDebug
```

The minimal SDK setup is command-line tools, `platform-tools`,
`platforms;android-37.0`, `build-tools;36.0.0`, and `ndk;27.0.12077973`.
For device testing, use one `system-images;android-35;default;arm64-v8a` image
with the `emulator` package. The local `obadh-api35` AVD has snapshots disabled
to avoid saving duplicate virtual-machine state. Android Studio, Google Play
images, extra SDK platforms, and a separate Gradle installation are optional.
Reuse the pinned Rust toolchain and the model
files in the neighboring iOS checkout. Keep the shared Gradle dependency cache
for subsequent builds; `cargo clean --manifest-path rust/obadh-jni/Cargo.toml`
can reclaim native build intermediates when needed (the next native build will
then recompile). The sourceable environment script applies only to the current
shell and does not change your default Java installation.

You need:

- **JDK 17 or 21.** Android Studio's bundled JDK works
  (`/Applications/Android Studio.app/Contents/jbr/Contents/Home` on macOS).
- **The Android SDK**, with platform 37.0, build tools 36.0.0 and an **NDK**. `ANDROID_HOME` should point
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
./scripts/build-rust-android.sh   # shipped arm64-v8a only; optional ABI arguments must be requested explicitly
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
paths and APK assets are not files. The APK **deflates** them (about 24 MB
smaller than storing them raw), and the copy decompresses as it goes: about
1.6-1.9 s on the test phone, once per app version. Until it finishes the keyboard
still types with the deterministic engine; the copy is verified byte-identical to
the source artifacts.

## Size

Measured on 2026-10-07 (decimal MB; the release build is unsigned and universal,
all three ABIs).

| | Before | After |
|---|---|---|
| Release APK (download) | 43.0 MB | **19.0 MB** |
| Debug APK | 52.5 MB | 28.1 MB |
| Installed on the phone (APK + extracted models) | about 83 MB | about 58 MB |

What is in the release APK: the three model groups are 39.8 MB raw and about 15.8 MB
deflated (autosuggest n-gram 29.5 → 10.9 MB, autocorrect FSTs 8.9 → 4.5 MB, emoji
1.3 → 0.3 MB); the native library is 1.9 MB across three ABIs; code and resources
are about 1.3 MB. A per-ABI split (a Play app bundle) saves only about 1.2 MB more,
because the native libraries are small.

The remaining cost is that the models exist twice on the device: compressed in the
APK and extracted in `filesDir/models` (38.9 MB). Removing the second copy needs the
engine to open a model from a file descriptor and offset, which means storing the
assets uncompressed again (the two are mutually exclusive) and a change in
`obadh_engine`, shared with iOS. Smaller models are the other lever, and also an
engine-side change.

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
| `release` | R8-minified, with a keep rule for the JNI entry class. It **builds** (`./gradlew :app:assembleRelease`, about 19 MB), has been run on a device, and is signed with the release key (see Signing). |

## Signing

Android refuses to install an unsigned APK ("App not installed as package appears
to be invalid"), and a plain `assembleRelease` with no key is unsigned.

| Build | Signed with | Use |
|---|---|---|
| `./gradlew :app:assembleRelease -PdebugSign` | the local debug key | Testing a release build on a device that already has the debug build: same signature, so it installs over it. **Not shippable.** Output: `app/build/outputs/apk/release/app-release.apk`. |
| `./gradlew :app:assembleRelease` with `keystore.properties` | your release key | The shippable build. |
| `./gradlew :app:assembleRelease` with neither | unsigned | Cannot be installed. |

`keystore.properties` lives at the repo root and is git-ignored, as are `*.jks` and
`*.keystore`. It has four keys: `storeFile` (a path, relative to the repo root or
absolute), `storePassword`, `keyAlias`, `keyPassword`. Create the key once with
`keytool -genkeypair -v -keystore <file>.jks -alias obadh -keyalg RSA -keysize 4096
-validity 10000`, and **back the keystore up outside the repo**: an app signed with a
lost key can never be updated. A debug-key build and a release-key build cannot be
installed over each other; uninstall one first, which clears its settings.

Verify a signature with `$ANDROID_HOME/build-tools/<version>/apksigner verify
--print-certs <apk>`.

## CI and releases

Two GitHub Actions workflows live in `.github/workflows/`.

- **`ci.yml`** runs on every push to `main` and every pull request: it checks out this repo
  and `unmukto-org/obadh-ios` (the models), syncs the models, builds the native library for
  the shipped ARM64 ABI, runs the unit tests and builds the debug APK, which it uploads as an
  artifact (14 days).
- **`release.yml`** runs when a tag `v*` is pushed. It refuses a tag that does not match
  `versionName` in `app/build.gradle.kts` or that has no matching section in
  [CHANGELOG.md](../CHANGELOG.md) (that section becomes the release notes), runs the tests,
  builds `assembleRelease` signed with the release key, checks the signature with
  `apksigner verify`, and publishes a GitHub release with `obadh-<tag>.apk` and its
  `.sha256`.

### The release key

The key is a 4096-bit RSA PKCS12 keystore (alias `obadh`, valid 10 000 days). CI reads it
from four repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. The workflow writes `keystore.properties` and
the keystore into the runner's temp directory and deletes them at the end. **The keystore is
the app's identity: an update must be signed with the same key, and a lost key cannot be
replaced.** Keep an offline backup of the `.jks` and its passwords; the secrets in GitHub
cannot be read back.

### Cutting a release

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Move the finished entries in `CHANGELOG.md` under a heading `## v<version> — <date>`.
3. Commit and push `main`, and wait for CI to pass.
4. Tag and push the tag:

   ```bash
   git tag -a v0.1.0 -m "Obadh 0.1.0"
   git push origin v0.1.0
   ```

5. The release workflow publishes the signed APK. Install it and check Settings › Version
   before announcing it: R8 can strip JNI-reachable code, so run the release build, not
   only the debug one.

Sideloading note: a debug build and a release build are signed with different keys, so
Android will not install one over the other; uninstall first, which clears the settings.


The combined native host requires `python3 scripts/prepare-heliboard.py`
after building Rust and before Gradle. This prepares the immutable source pin without
another Git history or SDK. `:engine` shares Obadh's existing JNI binaries; `:keyboard`
builds the upstream Latin native library. The current combined APK is ARM64 only so
that both native libraries are present for every shipped ABI. Local release testing:
`./gradlew --max-workers=4 :app:assembleRelease -PdebugSign`. The optional swipe binary
is downloaded only on user request and is not included in the release APK.
