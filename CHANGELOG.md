# Changelog

All notable changes. Dates are 2026-10-07 unless stated. The project has no
tagged release yet.

## Unreleased (0.1.0)

### Added
- **The keyboard.** An `InputMethodService` over a JNI layer on the engine's C ABI,
  drawn from nothing on a `Canvas`: one touch surface, nearest-key resolution,
  native-like backspace repeat, smart punctuation, double-space dari, `tq`/`qq`
  handling, loanword and gated auto-insert, next-word suggestions, personal
  learning, and a globe that opens the system keyboard picker.
- **Composing in the document.** The word is ordinary text, rewritten in place with
  exact deletion, instead of an Android composing span.
- **Emoji.** Inline suggestions in the ribbon's third slot, carried past the space;
  a custom-drawn panel with recents, categories, skin tones and `hasGlyph`
  filtering; English and Bangla search with the engine's transliteration; recents
  and per-emoji tones.
- **Tablets.** Compact, standard and extended families chosen from the smallest
  width, with Tab, Caps Lock, hide, a Bangla-numeral number row, and flick-down or
  long-press secondary glyphs.
- **Landscape.** Per-orientation tablet geometry and bottom-row weights; shorter
  phone rows and strip, with a capped, centred key block; cutout and side-bar
  insets; rotation rebuilds the layout.
- **The app.** A Compose app in the iOS brand language: drifting-glow background,
  floating mark, an animated setup diagram, grouped settings, About with the
  engine version through JNI, Privacy with Clear Learned Words. Keyboard state
  follows the system live. Adaptive launcher icon.
- **Debug tooling.** `KeyboardPreviewActivity` and launch extras for reviewing any
  layout or screen without taps (debug builds only).
- **Tests.** 35 unit tests, against the real artifacts for emoji.

### Changed
- **Download size 43.0 → 19.0 MB.** The models are deflated in the APK instead of
  stored raw. The first launch copies them to private storage as before, now
  decompressing as it goes (1.6-1.9 s on the test phone); the copies were verified
  byte-identical to the originals. Installed size falls from about 83 to about 58 MB.

### Added (build)
- Release signing from a git-ignored `keystore.properties`, and `-PdebugSign` to sign
  a release build with the debug key for on-device testing. An unsigned release APK
  cannot be installed.

### Fixed
- Words merging after a space: rewrites no longer delete by re-reading the text
  ([KI-003](KNOWN-ISSUES.md#ki-003); not confirmed on the reporting device).
- Black strip under the keyboard: the navigation-bar strip matches the keys.
- Duplicate Bangla-digit row and duplicate backspace on the extended tablet pages.
- Duplicate engine library packed into every ABI.

### Known
See [KNOWN-ISSUES.md](KNOWN-ISSUES.md), including the unverified touch behaviour
and the recents-preview quirk.
