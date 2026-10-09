# Obadh-Android: Modern Bangla Typing App for Android

[![CI](https://github.com/unmukto-org/obadh-android/actions/workflows/ci.yml/badge.svg)](https://github.com/unmukto-org/obadh-android/actions/workflows/ci.yml)

**Obadh** (অবাধ) is a project to modernize Bangla typing across all major
platforms. At its core is
[obadh_engine](https://github.com/nsssayom/obadh_engine), a deterministic
Roman-to-Bangla transliteration engine and runtime SDK written in Rust: fast,
accurate, dictionary-free at the core, with FST autocorrect and n-gram
autosuggest as separable layers. This repository is the **Android deliverable**:
a native keyboard (`InputMethodService`) for phones and tablets, built on that
engine and on the same philosophy as [obadh-ios](../obadh-ios).

The Android keyboard uses one native editor and gesture pipeline for Bangla and
English. Obadh supplies Bangla transliteration, correction, prediction and learning
through its C ABI; the Android foundation supplies English correction and optional
swipe typing. Shared settings apply to both languages. Enable Swipe typing in setup
or Gestures, then tap Download for the optional background transfer. See
[the integration guide](docs/heliboard-integration.md).

You type roman, it composes Bangla live.

Letter-key shortcuts: `tq` → **ৎ**, `qq` → **ঁ**. For example, `sotq` → **সৎ**
and `baqq` → **বাঁ**.

## What it does

- **Live native composition.** Roman input renders Bangla immediately through the
  engine. Android's composing region and editor transactions handle insertion,
  deletion and cursor updates. Mid-word edits preserve surrounding text.
- **Autocorrect that knows its place.** The ribbon shows the deterministic
  output first, then engine-ranked corrections. Optional auto-insert commits a
  correction on space only when a strict, measurable confidence gate passes, and
  tapping your own quoted spelling protects it. ([the gate](docs/autocorrect.md))
- **Next-word suggestions and personal learning** from the engine's bundled
  n-gram model plus a fingerprint-validated personal overlay.
- **Emoji, the way you actually say it.** Inline emoji suggestions for the word
  being typed (ভালোবাসা → ❤️), and a full emoji panel with categories, recents,
  skin tones, and English and Bangla search. ([the pipeline](docs/emoji.md))
- **Bangla numerals and punctuation:** ০–৯, `৳` and `।`, with double-space full
  stops in the active language. Numeric, email and password fields retain literal
  Android input behavior.
- **Native gestures.** Swipe space to switch Bangla/English, move upward from space
  for the cursor trackpad, or swipe backspace to delete. Hold X/C/V for native
  cut/copy/paste popups. Each shared control has one setting.
- **Android keyboard and Material settings.** Gboard-measured default geometry and
  light/dark colors, wallpaper/fixed/photo themes, key borders, number row, size and
  tablet split choices. Shared native toolbar, clipboard and emoji panels accompany
  organized Material preference screens.
- **On-device typing.** Models, learning and clipboard remain private on the device.
  Network access serves explicit optional swipe-library downloads and opt-in KLIPY GIF/sticker search.
  Recents cache small previews; offline sticker packs are excluded. See [media design, privacy and attribution](docs/stickers.md).

## Getting started

```bash
git clone <this repo> && cd obadh-android
./scripts/bootstrap.sh             # syncs models, builds the native library
./gradlew :app:testDebugUnitTest   # unit tests against the real artifacts
./gradlew :app:installDebug        # install on a connected device
```

Requirements: JDK 17 or 21 (Android Studio's bundled one works), the Android SDK
with an NDK, [rustup](https://rustup.rs) (the toolchain is pinned in
`rust/obadh-jni/rust-toolchain.toml`), and `cargo-ndk`. Then turn Obadh on in
**Settings › System › Languages & input › On-screen keyboard**; the app walks
you through it. Details and the engine-bump workflow:
[docs/build-and-release.md](docs/build-and-release.md).

Signed APKs are on the [Releases](https://github.com/unmukto-org/obadh-android/releases)
page; allow installs from your browser or file manager, install, then turn Obadh on.

## How it's built

The engine owns transliteration, correction ranking, and suggestion lookup; the
Android layer owns touch, layout, input-connection mutation, haptics, and
policy. They meet at a deliberately thin JNI layer over the engine's C ABI that
moves UTF-8 buffers and packed records, nothing else. The `:keyboard` module owns the native editor and keyboard views; `:engine` owns
the shared bridge. The small checked adapter is described in the integration guide.

| Doc | Covers |
|---|---|
| [architecture.md](docs/architecture.md) | Components, the JNI boundary, threading, the composer boundary, state and storage |
| [text-composition.md](docs/text-composition.md) | Legacy composition reference; current native behavior links to the integration guide |
| [autocorrect.md](docs/autocorrect.md) | The engine/client policy split and the auto-insert confidence gate |
| [emoji.md](docs/emoji.md) | Data, inline suggestions, the panel, search, recents, skin tones |
| [gboard-parity.md](docs/gboard-parity.md) | Native geometry, colors, feature scope and measured Gboard comparisons |
| [layouts.md](docs/layouts.md) | Legacy canvas layout reference |
| [testing.md](docs/testing.md) | Unit tests, what is and is not verified, manual checks |
| [build-and-release.md](docs/build-and-release.md) | Setup, the Rust bridge, device install, engine bumps |
| [KNOWN-ISSUES.md](KNOWN-ISSUES.md) | Open issues, evidence, and what remains unverified |
| [CHANGELOG.md](CHANGELOG.md) | What changed and when |

For the engine itself (the transliteration model, the artifacts, the philosophy
of a dictionary-free core) read the
[obadh_engine README](https://github.com/nsssayom/obadh_engine). For the iOS
counterpart, [obadh-ios](../obadh-ios) is the reference: where the two differ,
the docs here say so.

## Project layout

```
app/src/main/kotlin/org/unmukto/obadh/
  engine/      Composer and correction policy; legacy composition references
  keyboard/    Native service adapter and bilingual feature runtime
  emoji/       Data stores, the panel view, recents, skin tones
  settings/    Preferences, learned words, model installer, install state
  app/         The containing app (Compose): setup, settings, about, privacy
engine/src/     Shared Kotlin bridge to the engine C ABI
integration/heliboard/ Pinned native-host preparation, extension seam and tests
app/src/debug/ Debug-only native editor probe and legacy preview (excluded from release)
app/src/test/  Unit tests (real artifacts, no device)
rust/obadh-jni/ JNI shim over obadh_engine's cabi feature
scripts/        Bootstrap, model sync, native build
docs/           The documents above
```

## Testing

Behaviour is verified against the real thing where it can be: unit tests run
against the real generated artifacts, and a debug probe drives the actual native IME and framework editor. What cannot be verified without a device
(touch gestures, rotation, host-app quirks) is listed honestly in
[KNOWN-ISSUES.md](KNOWN-ISSUES.md). [docs/testing.md](docs/testing.md) has the map.


## License

The combined Android application is GPL-3.0-only; see [LICENSE](LICENSE).
HeliBoard/AOSP and icon notices remain in [third-party licenses](docs/third-party/heliboard).
Obadh Engine remains MIT licensed. The optional Google swipe library is a separate,
user-requested download and is not included in the APK or covered by the app's GPL.
