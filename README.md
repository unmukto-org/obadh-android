# Obadh Android

The Android deliverable of [Obadh](../obadh_engine): a native keyboard (`InputMethodService`)
built on the deterministic Roman-to-Bangla engine, following the same philosophy as
[obadh-ios](../obadh-ios).

- **Engine owns mechanism, client owns policy.** Rust does transliteration, autocorrect
  candidates and autosuggest lookup; Kotlin owns touch, layout, text mutation, haptics,
  and the auto-insert gate.
- **Thin C ABI.** `rust/obadh-jni` is a JNI shim: one function per engine C-ABI call, UTF-8
  buffers and packed little-endian records only. `ObadhBridgeClient` parses them, with one
  lock per handle.
- **Compose in the document, not in a composing span.** The word being typed is ordinary
  text, rewritten in place (`TextCompositionController`); no `setComposingText`.
- **Async autocorrect, generation-guarded.** Deterministic output renders synchronously.
- **No network.** The manifest has no `INTERNET` permission.

## Layout

```
app/src/main/kotlin/org/unmukto/obadh/
  engine/     bridge client, composer, composition controller, auto-insert gate
  keyboard/   InputMethodService, key view, ribbon, layout
  settings/   preferences, learned words, model installer
  app/        Compose setup walkthrough and settings
rust/obadh-jni/   JNI shim over obadh_engine's cabi feature
scripts/          sync-models, build-rust-android, bootstrap
```

## Build

```bash
./scripts/bootstrap.sh        # needs rustup + Android NDK; installs cargo-ndk, syncs models, builds .so
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

Models are copied from `../obadh-ios/Resources/ObadhModels` so both platforms ship the same
artifacts. Emoji, personal-overlay UI, long-press variants and Gboard-parity measurement are later phases.
