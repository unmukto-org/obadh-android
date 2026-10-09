# Testing

Behavior is verified against the real thing where that is possible without a
device, and the remainder is listed honestly in [KNOWN-ISSUES.md](../KNOWN-ISSUES.md).

## Unit tests (47)

```bash
./gradlew :app:testDebugUnitTest :keyboard:testDebugUnitTest
```

They are plain JVM tests: no emulator, no device, no Robolectric.

| Suite | Tests | Covers |
|---|---|---|
| `CoreLogicTest` | 11 | The `tq` / `qq` input rules and backspace units; stale autocorrect results being dropped; the auto-insert gate (unknown and completion channels never fire, protected words, the frequency floor, the 50× ratio on `manus`); in-place composition (append-only rendering, reshape, never deleting foreign text, the exact-deletion regression with a host whose key-event deletes land late); packed-record parsing; smart punctuation |
| `EmojiStoresTest` | 8 | Against the **real artifacts**: curated words, misses and normalization, the three-emoji cap with no skin-tone variants, the catalog and English search, skin-tone grouping, Bangla search (exact, prefix, miss, fuzzy), recents eviction, variant preferences |
| `KeyboardLayoutTest` | 17 | Family selection from smallest width; row structure per family; Tab and Caps Lock only where the family has them; the compact home row; the extended number row and pages that never resize; one backspace and one return per page; command-row structure; the unchanged phone layout; secondary glyphs; the number pad's digits and its lack of a language key; and the landscape rules |

`EmojiStoresTest` needs the artifacts: run `./scripts/sync-models.sh` first. If
they are absent it skips rather than fails.

## Actual native editor regressions

The debug-only `NativeKeyboardProbeActivity` supplies a real framework EditText;
its receiver drives the actual service, JNI library and InputConnection. Run:

```sh
source scripts/dev-env.sh
python3 scripts/prepare-heliboard.py
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/test-native-keyboard.py
```

The script checks both languages, fast boundaries, correction policy, next-word
prediction, shortcuts and pairs on/off, email/numeric fields, emoji selection,
double-space punctuation, cursor edits, return actions and language switches.
It also checks shared native controls, manual spelling protection, volume cursor
handling and sensitive clipboard exclusions. Probe overrides leave canonical app
settings unchanged. Learning and clipboard tests create local fixtures; use a
development emulator and clear those fixtures through the app's Privacy/Clipboard
controls afterward. Debug tooling is entirely excluded from the release APK.

Root JVM suites have 41 checks; six native combiner checks verify the seam,
including Roman snapshots retained across boundary flushes. Legacy canvas-layout
checks below are reference coverage, not native screenshot comparisons.

## Legacy preview, not a screenshot test

`KeyboardPreviewActivity` (debug only) renders the real keyboard view at a forced
width and orientation, so every layout can be reviewed on a phone. It is a review
tool, not an assertion: there is no automated screenshot comparison yet, and the
measured parity suite iOS has (against Apple's keyboard) has no Android
counterpart.

## What is not covered

- **Host coverage.** The emulator suite drives a real `InputConnection` and JNI,
  but physical devices and diverse third-party editors still need review.
- **No engine fingerprint pins** on Android, so a silent artifact swap on an
  engine bump would not fail a test. See [KNOWN-ISSUES.md](../KNOWN-ISSUES.md#ki-005).
- **Touch behaviour is checked by hand**, not by a test: nothing injects taps on the
  test phone, so the emoji panel, gestures and rotation follow the manual checklist.
- **A tablet.** Every tablet layout was reviewed through the preview on a phone.

## Device notes

Some phones will not accept injected input from `adb` (Xiaomi blocks it unless
"USB debugging (Security settings)" is enabled), and `adb shell ime enable/set`
needs `WRITE_SECURE_SETTINGS`. In that case a screenshot (`adb exec-out screencap
-p`) works but taps do not, so interaction has to be done by hand. The debug
activities accept launch extras so screens can be reached without taps:

```bash
adb shell am start -n org.unmukto.obadh/.app.MainActivity --es screen about
```

(`screen`: settings, preferences, correction, gestures, clipboard, shortcuts,
emoji, privacy, about, advanced, setup. Honoured only in debuggable builds. Setup now
uses one page rather than separate welcome/setup/done steps.)

## Manual checklist for a device

For the containing app, review the category index and Advanced overflow menu;
change a switch and a single-choice setting, then restart to check persistence.
Add, edit and delete a text shortcut, including duplicate and unsaved-change
handling. Check the clipboard deletion confirmation and About's build-details
disclosure. Review light/dark mode and large system text; preference rows must
grow with wrapped labels rather than overlap. The 2026-10-09 review used the
Android 15 ARM64 emulator at normal and 150% font size. Android 17 device
behavior remains unverified; compile SDK 37.0 supports the current Material
library while target SDK stays at 35.

1. Enable and select Obadh; the app's banner and setup screen update on their own
   when you choose it in the picker.
2. Type `ami` → আমি; `sotq` → সৎ; `baqq` → বাঁ; backspace removes `qq` as one unit.
3. Type `jukto borrno`; the two words stay separate.
4. Space, double-space (dari), numbers, symbols, shift, caps lock.
5. Open the emoji panel: scroll, jump categories, long-press a hand for tones,
   search in English and in Bangla, return.
6. On a tablet: every key, flick down and long-press on letters, Tab, Caps Lock,
   hide. Rotate with the keyboard open.
7. Switch away and back: the word is kept. Open recents with the keyboard up: the
   card must show the keys at the bottom (see "Window shape" in
   [text-composition.md](text-composition.md)).


## Optional swipe setup

In onboarding or Gestures, switch Swipe typing on. Check the native Download/Cancel
dialog; Cancel must leave the switch off. With connectivity disabled, Download must
show waiting and allow cancellation. Return to Home and kill the background settings
process (not Android's force-stop, which suppresses receivers), then restore network.
The transfer must finish and install without bringing settings to the foreground.
After completion, English word glide must enter a word; toggling off/on must reuse
the installed library. Check retry UI for a removed/failed download, and run
`SwipeLibraryVerifierTest` for corruption, oversized data and interruption cleanup.
Repeat download and typing in the optimized release: debug verification alone does
not catch native/R8 packaging failures. APK is ARM64 only for this migration preview.
