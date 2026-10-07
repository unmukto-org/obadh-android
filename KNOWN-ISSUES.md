# Known issues

Updated: **2026-10-07**. Only open issues and what remains unverified belong here.
Resolved work belongs in [CHANGELOG.md](CHANGELOG.md). Add dated evidence to the
relevant issue, and remove an entry when its acceptance checks pass.

**Scope:** `0.1.0`, unreleased. Device evidence comes from one phone: a Xiaomi
M2003J15SC on Android 12 (MIUI), 1080×2340, 440 dpi, used over wireless debugging.
There is no tablet and no other phone. Everything labelled *built, not verified*
was reviewed as a rendering or a unit test, not driven on a device.

| ID | Open issue | Status |
|---|---|---|
| [KI-002](#ki-002) | Touch behaviour never driven on a device | Built, not verified |
| [KI-003](#ki-003) | Merged words after a space (`jukto borrno`) | Fix shipped, not confirmed |
| [KI-004](#ki-004) | Tablet and landscape geometry not measured | Fitted from iPad, not Android |
| [KI-005](#ki-005) | No engine fingerprint pins or real-data calibration tests | Gap |
| [KI-006](#ki-006) | Live keyboard-state update not confirmed | Built, not verified |
| [KI-007](#ki-007) | Release build never run; app light mode never viewed | Unverified |

<a id="ki-002"></a>

## KI-002: Touch behaviour never driven on a device

`adb shell input` is refused by the test phone, so no tap, drag or long press has
been injected. These were built, and their layout reviewed, but not exercised:

- The emoji panel: fling, category jumps, long-press skin tones, ⌫ repeat.
- Emoji search: field, language chip, Bangla transliteration, results, keyboard
  beneath.
- The tablet flick-down and long-press secondary glyphs, Tab, Caps Lock, hide.
- Rotation with the keyboard open, and the closing of an open panel on rotation.
- The globe opening the system picker (confirmed built; not tapped).

**Acceptance:** each item on the [manual checklist](docs/testing.md#manual-checklist-for-a-device)
passes on a phone and a tablet.

<a id="ki-003"></a>

## KI-003: Merged words after a space (`jukto borrno`)

**Known:** the user reported the space before a word disappearing so two words
became one. **Suspected cause:** rewrites used `KEYCODE_DEL` events and re-read the
text until its length matched a target; a host that applies key events late would
make that loop over-delete. **Fix:** rewrites now delete exactly the known suffix
with one `deleteSurroundingText`. A unit test with a late-applying host covers it.

**Not confirmed:** the cause was never reproduced (the phone could not be driven),
and the user has not reported back that the problem is gone.

**Acceptance:** typing `jukto borrno` on the affected host keeps the space.

<a id="ki-004"></a>

## KI-004: Tablet and landscape geometry not measured

Tablet key weights, margins and gaps are the iPad measurements from obadh-ios.
Landscape tablet row heights (58, 64 and 56 dp), the phone landscape row height
(38 dp) and the 820 dp phone cap are engineering choices. None were compared with
Gboard or measured on an Android tablet. The family breakpoints (600, 720, 900 dp)
sit between common device widths but are unvalidated against real devices.

**Acceptance:** a measurement pass against Gboard on at least one device per family,
recorded as numbers in [docs/layouts.md](docs/layouts.md).

<a id="ki-005"></a>

## KI-005: No engine fingerprint pins or real-data calibration tests

iOS pins the autocorrect and autosuggest fingerprints and runs the auto-insert
calibration against the real artifacts, so an engine bump that silently swaps data
fails loudly. Android has neither: the gate's constants are copied from iOS and
tested structurally only, and no test loads the native library.

**Acceptance:** an instrumented test that opens the real artifacts, pins both
fingerprints, and re-runs the `manus`→মানুষ and `bondu`→বন্ধু calibration cases.

<a id="ki-006"></a>

## KI-006: Live keyboard-state update not confirmed

The app observes the enabled-input-methods and default-input-method settings, so
choosing Obadh in the picker should update the screen at once. The test phone was
on another keyboard and `adb` cannot change it, so the transition was never seen.
Static screens were verified in the correct state (the banner correctly said Obadh
was not the current keyboard).

**Acceptance:** with Settings open, choose Obadh in the picker; the banner clears
and, in setup, the flow advances without leaving the app.

<a id="ki-007"></a>

## KI-007: Release build never run; app light mode never viewed

`assembleRelease` succeeds (about 19 MB; signed with the debug key via `-PdebugSign`, or
unsigned) but the result has not been installed, and R8 can strip JNI-reachable code, so a release build must be run
before shipping. The app's UI was reviewed in dark mode only; light mode uses the
same palette as iOS but was not looked at.
