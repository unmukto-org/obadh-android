# Architecture

**Obadh** is a project to modernize Bangla typing across all major platforms.
Its core is [obadh_engine](https://github.com/nsssayom/obadh_engine), a
deterministic Roman-to-Bangla transliteration engine and runtime SDK in Rust
(dictionary-free core, FST-based autocorrect, n-gram autosuggest, a personal
learning overlay). This repository is the **Android deliverable**: an input
method service where everything real happens, and a small containing app for
setup and preferences, with the engine underneath reached through a deliberately
thin JNI layer over its C ABI.

This document covers the Android side. For the engine itself read the
[engine README](https://github.com/nsssayom/obadh_engine). For the iOS
counterpart, whose philosophy this follows, read
[obadh-ios/docs/architecture.md](../../obadh-ios/docs/architecture.md).

## Components

| Component | What it is |
|---|---|
| `ObadhInputMethodService` | The keyboard: an `InputMethodService`. All typing behaviour lives here and in the classes it owns. |
| `MainActivity` and the `app/` package | The containing app (Jetpack Compose): a first-run flow that asks its questions once, then a settings screen that asks nothing. It has no text input anywhere. |
| `KeyboardView` | One custom `View` that draws every key and receives every touch. Keys are not child views. |
| `EmojiPanelView`, `SuggestionBarView` | The other two custom-drawn views, built the same way. |
| `engine/` | The Kotlin side of the engine: bridge client, composer, composition controller, auto-insert gate. Pure Kotlin where possible, so it tests off-device. |
| `rust/obadh-jni` | A Rust `cdylib` that exposes the engine's C ABI to the JVM, one function per call. |
| `assets/ObadhModels` | The bundled binary artifacts: autocorrect FST, autosuggest n-grams, emoji catalog and Bangla emoji indexes. Synced from obadh-ios by `scripts/sync-models.sh`, so both platforms ship the same bytes. Git-ignored. |

Everything is drawn from nothing on a `Canvas`. No stock Android keyboard widget
is used, which is what lets the keyboard share its touch model, its geometry
tables, and its design tokens with the iOS keyboard.

## The engine boundary

Rust owns transliteration, autocorrect ranking, autosuggest lookup, and model
parsing. Kotlin owns Android UI, touch routing, asset installation, and
input-connection mutation. The boundary moves UTF-8 buffers and packed
little-endian records, nothing else. The full ABI contract (sizing conventions,
record formats, handle rules) is in the engine's vendored header and the
[engine README](https://github.com/nsssayom/obadh_engine#c-abi).

`rust/obadh-jni` adds no API. Each `Java_org_unmukto_obadh_engine_ObadhNative_*`
function marshals bytes into one `cabi` call, using the same snprintf-style
sizing the Swift client uses: a 1 KiB stack scratch, one retry on overflow. The
Kotlin side (`ObadhBridgeClient`) parses the packed lists and records, exactly as
the Swift client does, and checks the ABI version at load.

Unlike iOS there is no dead-stripping problem to work around. The engine is a
normal Rust dependency of a `cdylib`, which exports the JNI symbols directly.
`scripts/build-rust-android.sh` deletes the engine's own copy of the library
that `cargo-ndk` also emits, because our library links the engine statically.

Division of decision-making mirrors the code boundary: the engine ships
*mechanism and provenance* (candidates, channels, costs, frequencies); the
client owns *policy* (what auto-inserts, what renders, what learns). See
[autocorrect.md](autocorrect.md).

## Threading

The ABI forbids using one handle from two threads at once. That is a per-handle
contract, so `ObadhBridgeClient` holds **one lock per handle** (deterministic
engine, autocorrect, autosuggest). Do not collapse them into one: a keystroke's
microsecond transliteration must never queue behind a millisecond FST traversal
on another handle. No method takes more than one lock, so there is no ordering
hazard.

The service runs the expensive work on a single worker executor (autocorrect
queries, model loading, personal-snapshot saves, emoji search) and posts results
back to the main thread. The deterministic transliteration is synchronous and
stays on the main thread. Models are copied out of the APK and opened on the
worker at startup; until they are ready the keyboard still types, using the
deterministic engine only.

## The composer boundary

Keystrokes go to a composer that produces the deterministic transliteration
synchronously (rendered inline at once) and merges the expensive
autocorrect results asynchronously, **generation-guarded** so out-of-order or
stale results are discarded. New capabilities plug in at this boundary rather
than into key handling. Inline emoji suggestions are the example: a binary
search on a mapped index, safe on the keystroke path, so the composer holds the
suggester and the service only decides how to show it.

## State and storage

Preferences (haptics, auto-insert, emoji search language, onboarding step) are
plain `SharedPreferences`, the right tool for small state; there is no SQLite or
Room. Recent emoji, remembered skin tones, and protected ("kept") words are also
small key-value state. Personal autosuggest learning persists as an
engine-exported snapshot file in app-private storage, fingerprint-validated on
load so a stale snapshot from a different artifact generation is dropped rather
than imported. The app never makes a network request and declares no `INTERNET`
permission.

Android needs no equivalent of iOS's App Group or Full Access toggle: the
keyboard service and the containing app are the same process family with the
same private storage.

## Where Android differs from iOS

| Topic | iOS | Android |
|---|---|---|
| Native library | Static `xcframework` with a `#[used]` symbol table | `cdylib` per ABI, loaded with `System.loadLibrary` |
| Models | Read straight from the bundle | Copied once per app version to private storage (the engine opens paths; APK assets are not files) |
| Text mutation | `UITextDocumentProxy`, host-defined delete unit | `InputConnection`; rewrites use an exact `deleteSurroundingText`, the user's own backspace uses a key event |
| Keyboard switching | The globe cycles to the next keyboard | The globe opens the system keyboard picker |
| Layout families | Chosen from iPad portrait width | Chosen from `smallestScreenWidthDp` |
| Setup | Settings › Obadh › Keyboards (private URLs are rejected) | A public intent opens keyboard settings; a picker dialog selects it; both are observed live |
