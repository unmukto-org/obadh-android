# Text composition and input behavior

The decisions here were mostly forced by how Android treats input methods, and
several mirror [the iOS decisions](../../obadh-ios/docs/text-composition.md).

## Composing in the document, not in a composing span

The word being typed is ordinary text in the field, re-derived in place on each
keystroke, not an Android *composing region* (`setComposingText`). A composing
span is an IME composition that binds the insertion point to the region until it
is finished, and how cleanly it releases the cursor depends on each host editor.
A transliteration keyboard is not assembling one glyph from phonetic parts; it
produces words that should behave like any other text.

So Obadh inserts the Bangla directly with `commitText` and rewrites the current
word as letters arrive: append just the new code points when the rendering grows
(no flicker), delete the changed suffix when it reshapes. The cursor moves
freely everywhere, mid-text editing is a plain edit, and switching keyboards
mid-word keeps the word. The discipline this demands: track the exact string that
was inserted (`TextCompositionController.composedText`) and confirm it is still
before the cursor before rewriting, so a cursor move we did not observe never
deletes text we do not own. The service also stops tracking a word whenever
`onUpdateSelection` shows the cursor is no longer at its end.

## Exact deletion

When the rendering reshapes (a vowel sign becomes a conjunct, a correction
replaces the word) the changed suffix is deleted with a single
`deleteSurroundingText(n, 0)`, where `n` is the exact UTF-16 length of the text
we inserted and are replacing. The controller has already verified that text is
immediately before the cursor, so the count is known, not guessed.

The first version deleted by sending `KEYCODE_DEL` events and re-reading the
text until its length reached a target. That is fragile: if a host applies key
events after our next read, the loop sees the text unchanged and sends another
delete, removing a character it does not own. The reported symptom was the space
before a word disappearing so two words became one (`jukto borrno`). That
mechanism is our best explanation, not a confirmed diagnosis, and the fix has
unit-test coverage with a host that applies key-event deletes late; see
[KNOWN-ISSUES.md](../KNOWN-ISSUES.md#ki-003).

Key events are still used for the *user's own* backspace, where "delete one unit
the way this host does" is exactly what is wanted.

## Touch routing

One custom view draws every key and receives every touch. Keys are not child
views, so the gaps between keys and the padding around the outer keys are live
touch area, and a touch resolves to the nearest key: the row band by Y, then the
key containing X or the nearest centre. A key fires on release, with the key
resolved at the finger's final position, so sliding off a key onto its neighbour
types the neighbour. Holding backspace is the exception: it fires on press and
repeats.

## The suggestion ribbon

- The deterministic output renders first, then autocorrect candidates. When the
  typed literal is not a lexicon word it renders quoted (the "keep my spelling"
  affordance), and every shown slot is tappable.
- Accepting a candidate commits it with a trailing space and advances the
  autosuggest session, like the native keyboards.
- After a word commits, the ribbon can show next-word suggestions from the
  bundled n-gram model, merged with personally learned words.
- Up to three emoji take over the third slot for the current word, and stay
  there after the space that commits the word, until the next letter. Tapping one
  **replaces** the word: the typed text was the emoji's query. See
  [emoji.md](emoji.md).

## Space, dari, punctuation

- Space commits the deterministic output (or the gated auto-correction; see
  [autocorrect.md](autocorrect.md)). The space key always inserts: two deliberate
  spaces are two spaces.
- Double-space → `। ` (dari + space) is a *quick* shortcut: the second space must
  land within 0.35 s, only at the end of the text, only after a word character.
- Numerals and punctuation are handled on the Android layer: the number page
  emits Bangla numerals ০–৯, `৳` and `।` sit on the punctuation pages, and smart
  punctuation applies (`--`→`—`, `...`→`…`, curly quotes).
- The engine interprets `qq` as চন্দ্রবিন্দু. The composer preserves the original
  Roman keys, but backspace removes a completed `qq` as one input unit: `baqq` →
  `ba`. An unpaired final `q` is removed separately (`qqq` → `qq`).
- `tq` / `Tq` map to the engine's existing double-backtick খণ্ড ত signal: `sotq`
  → সৎ. A following `qq` takes precedence: `tqq` → তঁ. Backspace removes the
  whole modifier. The composer keeps raw keys separately from `engineInput`;
  preview, suggestions and auto-insert queries all use the same canonical input.
  The core engine is unchanged.

## Backspace

Holding backspace follows a native-like curve: immediate delete, fast character
repeat, then word chunks on a sustained hold (`BackspaceRepeatPolicy`). While a
word is being composed, a character-unit backspace edits the Roman buffer and
re-renders; a word-unit backspace drops the word being typed.

## The globe

The globe finishes the current word and opens the **system keyboard picker**
(`InputMethodManager.showInputMethodPicker`). It deliberately does not cycle to
the next keyboard on its own: the user chooses. There is no English typing mode;
Obadh does one thing, and choosing another keyboard is how you type English.
This differs from iOS, where the globe cycles to the next keyboard.

## Insets and rotation

The keyboard is padded by the navigation-bar and display-cutout insets on the
left, right and bottom, so keys stay clear of a camera cutout or a side
navigation bar in landscape. The navigation-bar strip under the keyboard is
coloured to match the keys. Rotation rebuilds the layout and closes any open
emoji panel, because that panel's height was measured against the old layout.
Extract (fullscreen) mode is turned off: the keyboard is never laid out against
the whole display.

## Window shape

The input view is a full-display-height transparent `FrameLayout` with the keys
(`keyboardColumn`) anchored to its bottom, not a view only as tall as the keys.
The system composites the keyboard into the host app's recents screenshot from the
top of its capture area, so a window exactly as tall as the keys is drawn at the top
of the app card. A display-height surface lands the keys at the bottom, which is
what other keyboards on the test phone do.

To still behave as a bottom keyboard, `onComputeInsets` reports the keys' top as the
content and visible inset (the host app resizes above the keys) and uses
`TOUCHABLE_INSETS_REGION` limited to the keys' rectangle (touches above fall through to the
app). The root's minimum height is the display height and is refreshed on rotation.
