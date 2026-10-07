# Obadh-Android: Modern Bangla Typing App for Android

**Obadh** (অবাধ) is a project to modernize Bangla typing across all major
platforms. At its core is
[obadh_engine](https://github.com/nsssayom/obadh_engine), a deterministic
Roman-to-Bangla transliteration engine and runtime SDK written in Rust: fast,
accurate, dictionary-free at the core, with FST autocorrect and n-gram
autosuggest as separable layers. This repository is the **Android deliverable**:
a native keyboard (`InputMethodService`) for phones and tablets, built on that
engine and on the same philosophy as [obadh-ios](../obadh-ios).

You type roman, it composes Bangla live.

Letter-key shortcuts: `tq` → **ৎ**, `qq` → **ঁ**. For example, `sotq` → **সৎ**
and `baqq` → **বাঁ**.

## What it does

- **Live transliteration as real text.** The word you are typing is ordinary
  text in the field, not an Android composing span. The cursor moves freely,
  mid-text editing just works, and switching keyboards mid-word keeps the word.
  ([why](docs/text-composition.md))
- **Autocorrect that knows its place.** The ribbon shows the deterministic
  output first, then engine-ranked corrections. Optional auto-insert commits a
  correction on space only when a strict, measurable confidence gate passes, and
  tapping your own quoted spelling protects it. ([the gate](docs/autocorrect.md))
- **Next-word suggestions and personal learning** from the engine's bundled
  n-gram model plus a fingerprint-validated personal overlay.
- **Emoji, the way you actually say it.** Inline emoji suggestions for the word
  being typed (ভালোবাসা → ❤️), and a full emoji panel with categories, recents,
  skin tones, and English and Bangla search. ([the pipeline](docs/emoji.md))
- **Bangla numerals and punctuation:** ০–৯ on the number page, `৳` and `।`
  (dari) on the punctuation pages, quick double-space for dari, smart
  punctuation.
- **Phones and tablets, portrait and landscape.** Three tablet families chosen
  from the device's smallest width, each with its own row structure, plus
  landscape geometry for phones and tablets. ([the layouts](docs/layouts.md))
- **No network.** The manifest declares no `INTERNET` permission, so nothing you
  type can leave the device.

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

## How it's built

The engine owns transliteration, correction ranking, and suggestion lookup; the
Android layer owns touch, layout, input-connection mutation, haptics, and
policy. They meet at a deliberately thin JNI layer over the engine's C ABI that
moves UTF-8 buffers and packed records, nothing else. The keyboard itself is
drawn from nothing on a `Canvas`: no stock Android keyboard widget is used.

| Doc | Covers |
|---|---|
| [architecture.md](docs/architecture.md) | Components, the JNI boundary, threading, the composer boundary, state and storage |
| [text-composition.md](docs/text-composition.md) | Why not composing spans, exact deletion, touch routing, the ribbon, space and dari, backspace, the globe |
| [autocorrect.md](docs/autocorrect.md) | The engine/client policy split and the auto-insert confidence gate |
| [emoji.md](docs/emoji.md) | Data, inline suggestions, the panel, search, recents, skin tones |
| [layouts.md](docs/layouts.md) | Phone, tablet families, landscape, secondary glyphs, the debug preview |
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
  engine/      Bridge client, composer, composition controller, auto-insert gate
  keyboard/    InputMethodService, key view, ribbon, layouts, theme
  emoji/       Data stores, the panel view, recents, skin tones
  settings/    Preferences, learned words, model installer, install state
  app/         The containing app (Compose): setup, settings, about, privacy
app/src/debug/ Debug-only keyboard preview (not in release builds)
app/src/test/  Unit tests (real artifacts, no device)
rust/obadh-jni/ JNI shim over obadh_engine's cabi feature
scripts/        Bootstrap, model sync, native build
docs/           The documents above
```

## Testing

Behaviour is verified against the real thing where it can be: unit tests run
against the real generated artifacts, and a debug preview renders the real
keyboard view at any device width. What cannot be verified without a device
(touch gestures, rotation, host-app quirks) is listed honestly in
[KNOWN-ISSUES.md](KNOWN-ISSUES.md). [docs/testing.md](docs/testing.md) has the map.
