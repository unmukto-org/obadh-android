# Native Android integration

The application uses one Android editor, keyboard view and gesture pipeline for
Bangla and English. The Material settings application retains Obadh branding;
HeliBoard/AOSP attribution appears in About. Only bn-BD and en-US are registered.

`integration/heliboard/upstream.json` pins HeliBoard 4.2-beta1 at immutable commit
`bc2b91189d60692090bcf30448ba35a9feef8110`. The combined application is GPL-3.0-only;
Obadh Engine retains its MIT license. Source notices remain intact.

## Ownership

| Responsibility | Owner |
| --- | --- |
| Native editor transactions, composing regions, selection, gestures, toolbar and layouts | `:keyboard`, the pinned Android foundation |
| English spelling, correction and learning | Native US dictionary and personalization |
| Bangla transliteration | `ObadhCombiner` → shared JNI → Obadh Engine C ABI 2 |
| Bangla corrections, loanwords, prediction and personal learning | `NativeObadhFeatures` → shared C ABI bridge |
| Correction acceptance policy | Existing `KeyboardComposer` and `AutoInsertGate` |
| Bilingual emoji search, inline candidates and per-emoji variants | Obadh artifacts through the extension seam; native panel and recents |
| Shortcuts, pairs, field handling, return actions and shared settings | One root configuration snapshot and native `InputLogic` transactions |
| Product settings, onboarding and data deletion | Root Material application |

`ObadhExtension` is the narrow interface between the native host and the root
runtime. Checked patches call it at the combiner, suggestion, commit, emoji and
editor boundaries. The root service subclasses the native service and adds only
configuration, migration and optional volume-key cursor handling. The previous
service and canvas UI are unregistered legacy references, removed by release R8.

Both languages use the same editor transactions for shortcuts and paired symbols.
Email/URL fields use literal English when smart fields are enabled; passwords and
numeric fields always remain literal. Mid-word phonetic edits own only the new
fragment and preserve surrounding text. Return actions commit the current word
before submitting. Double-space full stops accept Bangla combining marks and use
native locale punctuation, including reversal by backspace.

Bangla ordinary correction is opt-in and follows the calibrated acceptance gate.
Exact English loanwords commit their Bangla match independently of English
correction and the ordinary Bangla toggle. The literal spelling remains selectable
in the strip; manual spellings are protected from ordinary auto-insert. English
spelling and autocorrection controls affect English without suppressing Bangla
suggestions. Bangla does not maintain a duplicate native learning dictionary.

## C ABI and performance

The JNI boundary marshals UTF-8 buffers, packed records and opaque handles; it
introduces no alternate Rust API. The bridge checks ABI compatibility. A bounded
1024-byte scratch buffer retries on overflow. Each handle has its own lock: a fast
transliteration handle cannot queue behind FST traversal on another handle.

The combiner caches its rendering and retains its Roman snapshot across boundary
flushes. Model installation and initialization happen once on a worker; typing is
available before they complete. Native suggestion workers use immutable composer
snapshots, coalesced requests and generation checks. A bounded 128-entry correction
cache avoids repeating FST queries for the same Roman word. Boundary correction
uses the latest query on the existing worker so fast typing cannot commit a stale
loanword result. Cursor reads for pairs happen only on relevant symbol/delete
keys. Personal snapshots are coalesced and written with `AtomicFile`; emoji search
runs away from the UI thread and rejects stale results.

Bangla next-word queries use the personalized session C ABI only when its bounded
committed context matches the actual editor context. Otherwise they use the
model-only explicit-context C ABI. Incognito and disabled learning always use
model-only queries, without swapping snapshots or allocating a second model.
Learning completion requests a fresh generation-checked suggestion update. The
native commit hook passes its existing pre-commit n-gram context; cursor changes,
sentence boundaries and language changes discard stale session context before
learning. Existing editor words are never replayed into the personal model.

Emulator handler timings are diagnostic, not physical-device latency guarantees.
Most ordinary letter events measured below 1 ms; cold queries, model startup,
editor IPC and device scheduling can take longer. No zero-latency claim is made.

## Shared settings and storage

The root application owns canonical `obadh_prefs`. Explicit private broadcasts
send complete snapshots to `:keyboard`, which owns native preferences and live
keyboard state. This avoids stale cross-process SharedPreferences caches. Shortcut
edits use the same delivery path. Native keyboard caches are invalidated when
layout-affecting settings change.

Both languages share clipboard, gesture, preview, sound, haptic, pair, shortcut,
field and return-action controls. Language-specific spelling policies remain
separate because their engines differ. Emoji search accepts English, Bangla and
Roman Bangla regardless of the preferred starting keyboard.

A one-time migration preserves old clipboard pins and emoji recents. Privacy
commands clear data in its owning keyboard process and acknowledge completion
before the Material UI shows success. Learned-data deletion clears Bangla personal
snapshots/protected words and native English histories/caches. Clipboard deletion
removes pins, database rows and stored files. Password, incognito and sensitive
clipboard data are excluded from collection.

## Appearance and keyboard geometry

Owned `ObadhColors` palettes drive keys, popup keys, suggestion strip, emoji,
clipboard and tools. Light/dark selection, wallpaper colors, key borders, size,
number row and language key are canonical settings shared by both languages.
Default colors and adaptive key geometry are measured against Gboard System Auto.
The language picker honors the selected keyboard appearance and lists only Bangla
and English, with Android's other-keyboards picker available separately.

Small checked hooks enable the upstream split-layout flag and replace tablet extra
keys with the measured QWERTY rows. Wide split layouts duplicate the center Latin
keys and retain one spacebar. Automatic splitting is disabled while floating;
explicit Full/Split controls still pass through the same native layout machinery.
The toolbar split toggle is offered in Automatic mode; an explicit Full/Split choice
is controlled from the shared Preferences page, avoiding a conflicting inactive toggle.

My themes stores one bounded, metadata-stripped source per explicitly saved photo,
with numerical crop/brightness parameters. Only Apply creates the one atomically
replaced private JPEG that the IME reads. Decode,
orientation, bounded downsampling and dimming happen off the main thread. The IME
uses the processed file and invalidates its background cache when its revision
changes. Forced appearance and photo revisions mark the native theme for reload,
so the existing view cannot retain stale colors or images. It never reads the source
picker URI during typing. No photo service or
additional image library is used.

## Optional Bangla and English swipe typing

The same Material switch appears in setup and Gestures. Enabling it without an
installed decoder opens Download/Cancel. DownloadManager owns the background
transfer, notifications, connectivity waiting and network retry. WorkManager
verifies a size limit and pinned per-ABI SHA-256 away from input threads, then
atomically installs a read-only library. Cancellation and installation share
ownership; late completion cannot activate a cancelled request. Errors have a
Retry action. Off/on reuses the installed file; activation restarts only the
keyboard process because loaded native libraries cannot be replaced safely.

The Google decoder is not included in the APK. Its source is pinned to OpenBoard
commit `46fdf2b550035ca69299ce312fa158e7ade36967`; no redistribution grant has been
verified for bundling. The ARM64 file is 1,112,352 bytes. Typing and learning stay
on-device; network permission serves this explicit optional download.

Bangla reuses this decoder through an app-owned phonetic dictionary derived from
Obadh's existing FST and verified through its C ABI. The checked factory seam
leaves upstream English unchanged. See [Bangla glide](bangla-glide.md) for model
provenance, regeneration, editor integration, device limitations and validation.

## Build and upstream updates

Use the existing JDK 17, SDK, NDK and single ARM64 emulator. No additional
language dictionaries, Git history or second upstream build tree are needed.

```sh
source scripts/dev-env.sh
python3 scripts/prepare-heliboard.py
./gradlew :app:assembleDebug :app:testDebugUnitTest :keyboard:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/test-native-keyboard.py
./gradlew :app:assembleRelease -PdebugSign
```

The final command creates an optimized, non-debuggable APK using the existing
local testing key: `app/build/outputs/apk/release/app-release.apk`. Publisher
signing uses `keystore.properties` instead. Debug probe activities and receivers
are excluded from release.

The ignored upstream checkout is generated from the pin. Tracked integration
files contain the preparation recipe, checked patches, adapter and tests. A small
pristine-source cache restores patched files before each preparation, allowing
repeatable offline preparation after initial download. Expected-snippet checks
fail on upstream drift. Unused engines, main layouts, dictionaries and interface
translations are physically pruned; required symbol/number machinery is retained.

For an update, change the immutable pin in a candidate workspace, run preparation,
resolve any checked seam drift, run unit and real-editor regressions, then build
and smoke-test the optimized release. Keep business logic in the owned extension;
avoid editing generated source or replacing English dictionary logic.

## Validation

The debug-only probe drives a real framework EditText and the actual IME, including
1 ms event scheduling for fast-boundary races. `scripts/test-native-keyboard.py`
checks both languages, composition/deletion, loanwords, opt-in correction,
prediction, shortcut/pair switches, literal fields, emoji selection/search,
double-space punctuation, cursor edits, return actions and language changes.
Unit coverage includes six combiner checks and the real Obadh artifacts. Swipe
verification tests cover corruption, size bounds, cancellation and partial cleanup.
Manual emulator checks cover download waiting/cancellation/retry, process death,
release activation and real English/Bangla glide gestures. Physical-device and diverse
host-editor testing remain additional release coverage.

Explicit tablet layout choices carry a small revision in the canonical preference
snapshot. Selecting Automatic again clears native portrait/landscape/folded split
overrides atomically before reloading the keyboard. Toolbar overrides remain useful
until that explicit choice; changing a theme or unrelated preference preserves them.
There is no new work on the typing path. The native layout regression exercises
this reset through actual settings and toolbar taps in both languages.
