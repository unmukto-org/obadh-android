# Testing

Behavior is verified against the real thing where that is possible without a
device, and the remainder is listed honestly in [KNOWN-ISSUES.md](../KNOWN-ISSUES.md).

## Unit tests (51)

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

On a reused isolated QA AVD, use Privacy → Delete learned words before rerunning
this suite: its deliberate literal-spelling test protects the fixture spelling across
sessions. Do not clear a personal device to run automation.

The 57 checks cover both languages, fast boundaries, correction policy, next-word
prediction, personal OOV words in the actual strip, incognito/learning-off queries,
shortcuts and pairs on/off, email/numeric fields, emoji selection,
double-space punctuation, cursor edits, return actions and language switches.
It also checks shared native controls, manual spelling protection, volume cursor
handling and sensitive clipboard exclusions. Probe overrides leave canonical app
settings unchanged. Learning and clipboard tests create local fixtures; use a
development emulator and clear those fixtures through the app's Privacy/Clipboard
controls afterward. Debug tooling is entirely excluded from the release APK.

Root JVM suites have 41 checks; six native combiner checks verify the seam,
including Roman snapshots retained across boundary flushes. One palette test checks WCAG 4.5:1 label contrast for the system light/dark themes.
Three catalog checks cover fixed reference RGB values, immutable theme IDs, 25 light/28 dark
gradient counts, monotonic stops, numerical meshes and measured key opacity. Legacy canvas-layout
checks below are reference coverage, not native screenshot comparisons.

## Actual touch regressions

```sh
python3 scripts/test-native-touch.py
```

This reads the renderer's key coordinates but sends real Android touch events for
letters, globe, long-space language picking, space-swipe switching and English
word glide, plus typing/restoration in one-handed and floating modes for both
languages. The optional swipe decoder must already be installed via the app's
switch. Touch fixtures use the same debug editor and do not add an emulator image.

## Gboard geometry and theme comparison

The real installed Gboard and Obadh are measured with actual touch input on one
resized Android 15 ARM64 emulator. This avoids extra emulator images. See
[gboard-parity.md](gboard-parity.md) for the pinned reference, profiles, tolerances,
results and remaining physical-device validation.

```sh
python3 scripts/keyboard-parity.py --keyboard gboard --output build/parity/gboard-system
python3 scripts/keyboard-parity.py --keyboard obadh --output build/parity/obadh \
  --compare-with build/parity/gboard-system
```

Select Gboard System Auto, key borders on and English QWERTY first. Select Obadh
System Auto / borders on / default height / automatic tablet layout.
The harness checks the keyboard is actually visible and the theme has settled,
then restores viewport, rotation, system night mode and selected IME even on failure.
Screenshots and JSON remain ignored build artifacts; no Google artwork is packaged.

Live appearance regression:

```sh
python3 scripts/test-native-appearance.py
```

This changes the actual Material settings, then samples the rendered keyboard
background and key fills in both languages. It checks repeated Light/Dark switches,
wallpaper palettes and border removal without restarting the IME. This catches the
case where preference values update but a cached view still renders the old theme.
It also verifies Cancel leaves the active theme unchanged. Photo regression:

```sh
ANDROID_SERIAL=emulator-5554 python3 scripts/test-native-photo.py
```

On the isolated rooted QA AVD, this creates a tiny fixture, exercises the system picker,
crop/brightness, real activity rotation, Done/Cancel without saving a gallery image, Apply, actual bilingual typing,
re-edit/back and deletion with active-theme fallback. It removes its own gallery theme
and device photo. It does not delete existing themes. Always set `ANDROID_SERIAL`
when the Gboard comparison emulator is also running.

Real toolbar interactions:

```sh
ANDROID_SERIAL=emulator-5554 python3 scripts/test-native-toolbar.py
```

The eleven checks touch the seven fixed controls, the in-keyboard tools grid and Back,
language switching, clipboard, comma/emoji hold, private theme destination, microphone
placeholder and password guards in both languages. They also verify the standard
Android preferences route. Debug probes only read control coordinates for these touches;
they do not invoke the tested actions. Reset the fixture to its alphabet layout before
starting a new editor because native utility panels intentionally retain their state.

The debug `personalization_probe` command creates isolated C ABI handles, trains a
synthetic OOV word and checks learned predictions, model-only incognito results,
editor-context mismatch, learning-context reset, snapshot preservation and deletion. It frees its handles
and does not change the user's personal dictionary.

## What is not covered

- Physical devices, vendor fonts/insets, Android 17 runtime and diverse third-party
  editors still need release validation. Emulated tablet configurations are measured;
  this is not a claim of physical tablet certification.
- Handler timings describe the emulator, not a hardware touch-to-photon guarantee.
- Legacy preview tests describe the previous canvas layout and are reference coverage.
- Engine model fingerprints are exposed through the C ABI but not yet pinned in CI.

## Native release packaging

```sh
python3 scripts/test-apk-native.py
python3 scripts/check-apk-native.py app/build/outputs/apk/release/app-release.apk
```

Four safety counterexamples check page-rounded RELRO protection. The actual APK
check verifies ARM64-only ELF LOAD alignment, safe RELRO ranges, uncompressed native
entries and 16 KB ZIP payload alignment without extracting another build tree.
The Rust and English libraries use both max/common-page-size linker flags. AndroidX
path has a partial RELRO page with its mutable data on a separate page; the check
verifies no writable LOAD bytes outside RELRO are protected by Bionic's outward
rounding. See [Bionic's implementation](https://android.googlesource.com/platform/bionic/+/refs/heads/main/linker/linker_phdr.cpp).
These static checks do not replace runtime validation on a 16 KB kernel. The
pinned optional decoder has an unsafe mutable tail and is not loaded or offered
on devices with pages larger than 4 KB. Ordinary English and Bangla remain available.

## Device notes

Some phones will not accept injected input from `adb` (Xiaomi blocks it unless
"USB debugging (Security settings)" is enabled), and `adb shell ime enable/set`
needs `WRITE_SECURE_SETTINGS`. In that case a screenshot (`adb exec-out screencap
-p`) works but taps do not, so interaction has to be done by hand. The debug
activities accept launch extras so screens can be reached without taps:

```bash
adb shell am start -n org.unmukto.obadh/.app.MainActivity --es screen about
```

(`screen`: settings, appearance, preferences, correction, gestures, clipboard, shortcuts,
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
6. On a tablet: every key and long-press symbols, split/full layout, keyboard
   tools and rotation with the keyboard open.
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
