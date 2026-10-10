# Bangla glide typing

One Swipe typing switch enables English and Obadh Bangla. In Bangla, glide over
the phonetic spelling on the existing QWERTY layout: `ami` → `আমি`, `bhalo` or
`valo` → `ভালো`, `tumi` → `তুমি`. Suggestions and committed text are Bengali.
The same optional downloaded decoder, native trails, candidate strip, editor
transactions, language controls and responsive keyboard geometry serve both
languages. Tap typing continues to handle spellings outside the glide vocabulary.

## Integration

The foundation's background dictionary loader calls one checked extension seam
in `DictionaryFactory.getMainDictsForLocale`. English returns to upstream
unchanged. Bangla supplies an app-owned, gesture-only `Dictionary` backed by
`ReadOnlyBinaryDictionary`; there is no custom touch tracker or second keyboard
service. The decoder uses Latin key geometry internally. The outer dictionary
locale and every emitted candidate remain Bangla. Roman search keys are mapped
to Bengali shortcut targets before preview, suggestions or editor insertion.
The foundation's validity check recognizes those mapped words, instead of
incorrectly searching for them as Roman keys. Language changes preserve the
gesture's pending automatic space, so alternating Bangla and English swipe words
does not join them together.

The gesture boundary check also recognizes Unicode combining marks, so Bangla
words ending in a dependent vowel sign receive the same automatic word separation
as words ending in letters. Tap → glide and glide → tap transitions both preserve
word boundaries, including across language changes.

Tap transliteration, correction, personal learning, prediction and field policy
remain in Obadh's existing engine C ABI v2. The adapter returns no tap candidates.
Gesture commits use the existing editor and learning path. The main model is
read-only; incognito does not create another personal dictionary. Password,
numeric and phone fields stay literal. Smart email/URL fields suppress Bangla
gesture conversion under the same setting as tap typing. Disabling Swipe typing
stops gesture queries in both languages. Enabling an installed decoder works
without another download or an IME restart.

The model is copied and SHA-256 checked on the background loader, then opened
natively. Extraction uses bounded streaming buffers, an atomic rename, corruption
recovery and cleanup restricted to the app-owned `ObadhGesture` directory. No
corpus, network call or model extraction runs for each touch event. Decoding and
target mapping run on the foundation's gesture worker. Debug timing logs contain
durations and thread names, without words; release builds omit them.

## Rebuilding the model

```sh
source scripts/dev-env.sh
cd rust/obadh-jni
cargo run --release --locked --features gesture-tools --bin gesture-lexicon -- \
  ../../app/src/main/assets/ObadhModels/autocorrect/bn.fst ../../build/bangla-gesture.tsv
cd ../..
python3 scripts/encode-bangla-gesture.py build/bangla-gesture.tsv
python3 scripts/check-bangla-gesture.py
```

The optional host-only Rust tool reads the same Bangla FST already used by Obadh.
It derives inverse phonetic spellings and accepts a spelling only when the public
`obadh_transliterate` C ABI reproduces the exact word. Common `bh`/`v`, `ph`/`f`
and `y`/`z` alternatives undergo that same check. Case folding describes gesture
geometry; original verified Bengali targets resolve phonetic case ambiguities by
word frequency. This tool is excluded from default Android Rust builds and changes
no runtime JNI or engine ABI.

CI checks the engine pin, source-FST hash, size and asset checksum. An engine or
model update cannot silently retain an incompatible derived gesture vocabulary.

The current model selects 60,000 frequency-ranked words (frequency ≥ 20, at most
24 Bengali code points), with 66,494 spellings on 63,845 lowercase paths. The
static AOSP format 202 is the format of the bundled English dictionary. The
2,441,868-byte derived asset is compressed in the APK. Its metadata pins engine
version, ABI, source-FST checksum, size and output checksum. There is no extra
bundled language, model download or training corpus. Rebuild it when updating
the engine/models; commit the compact derived asset and metadata together.

The vocabulary is Obadh-derived work. Obadh Engine/model attribution remains in
the existing MIT notice and About → Obadh Engine. The adapter/tooling are GPL-3.0
under this app's license. AOSP/HeliBoard attribution remains in About; the Google
decoder stays a separately downloaded optional component.

## Validation and limits

Run `ANDROID_SERIAL=emulator-5554 python3 scripts/test-bangla-glide.py` after
installing debug and enabling Swipe typing. It checks the asset using the actual
native dictionary, real continuous touch paths, Bengali-only candidates, aliases,
word spacing, tap/glide transitions, punctuation, whole-word backspace, bilingual
globe/space switching, protected fields, incognito, one-handed/floating layouts,
phone/tablet portrait/landscape geometry and unchanged English decoding. It
restores the viewport and canonical native configuration, and uses the existing QA
AVD. The actual app switch was tested off/on across both languages without a new
download/restart. A corrupt derived cache was repaired on cold startup with a
matching SHA-256; the fixture reselects Obadh because Android chooses a fallback
IME when its selected service is deliberately force-stopped.

2026-10-09: all word/boundary/field/layout cases passed on the existing Android 15
ARM64 AVD. Across 26 debug decode-and-mapping samples, median was 770 µs, p95
1,138 µs and maximum 1,204 µs, all on `InputLogicHandler`. These are decoder
measurements on an emulator, not an end-to-end physical-device latency guarantee.
The model contributes 1,049,443 compressed bytes to the APK, plus one 2,441,868-byte
private extracted copy for native access. No new emulator image was installed.

All 62 existing native editor regressions also passed with the new dictionary
adapter: C ABI/engine version, loanwords, correction calibration, personal/model-only
prediction, shortcuts, pairs, emoji, cursor edits, shared controls and clipboard
privacy. Use clean learned-word fixtures on the QA AVD for the calibration suite;
its later manual-spelling test deliberately protects `মানুস`.

To test the exact optimized APK without Obadh debug components, first build/install
the tiny independent recipient with `scripts/build-media-recipient.sh`. Record the
normal phone renderer coordinates while debug is installed, then install release:

```sh
ANDROID_SERIAL=emulator-5554 python3 scripts/test-release-keyboard.py --record-geometry
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
ANDROID_SERIAL=emulator-5554 python3 scripts/test-release-keyboard.py
```

The smoke path uses actual language-picker/toolbar/key touches and a separate
recipient UID/task. It verifies continuous Bangla/English word glide plus GIF and
WebP sticker decoding, read-only seekable delivery and return to the same editor
without changing the text. No messages are sent. It requires the existing opted-in
swipe decoder and a privately configured KLIPY testing build; no API key is committed.

The optimized 0.2.3 APK built from `d558f7a` passed this smoke test on 2026-10-09:
continuous Bangla phrase glide, unchanged English glide, animated WebP sticker and
GIF imports across recipient UIDs, with the original editor task and text preserved.
The recipient advertises each tested MIME type separately so compact WebP cannot
silently substitute for the GIF test. Actual Samsung Messages and Messenger phone
imports remain to be retested; emulator results do not establish those app-specific
results or carrier delivery.

This is a phonetic word-glide vocabulary, not a trained Bangla gesture language
model with contextual n-grams or exhaustive spelling coverage. Arbitrary names,
rare words and spellings not accepted by the engine still use tap typing. Large
human gesture-accuracy studies and physical-device latency/ergonomics testing
remain additional validation. Like English glide, it requires the optional
decoder and currently supports 4 KB page-size devices; the pinned decoder is
withheld on incompatible 16 KB devices.
