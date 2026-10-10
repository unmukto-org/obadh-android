# Changelog

All notable changes. Dates are 2026-10-07 unless stated. A release is an
annotated `v<version>` tag; pushing it builds, signs and publishes the APK
([docs/build-and-release.md](docs/build-and-release.md#releasing)).

## Unreleased

## v0.2.2 — 2026-10-09

- Fixed GIF/sticker selection returning to Obadh settings instead of the original
  chat. The private picker now owns a separate transient task and removes that
  task when closed, preserving the recipient's activity and draft. Allow a
  bounded reconnection grace period without dropping selections when Android
  or the receiving app takes longer than five seconds to restore input.
- Media delivery now provides seekable read-only Android file descriptors over
  its bounded RAM buffer. Chat importers can seek, reopen and decode attachments
  without `ESPIPE`; original animations remain off disk.
- Added an independent recipient APK with a separate UID/task, asynchronous
  importing, URI permission checks and native image decoding. The earlier
  same-APK editor fixture did not cover cross-app task routing or seekable reads.

## v0.2.1 — 2026-10-09

- Upgraded the engine to 0.9.5 through the unchanged C ABI v2. Ship its matching
  loanword model with a release commit/checksum pin, including `ok`/`okay` → `ওকে`.
  App version/code changes refresh extracted models on existing installations.
- Recognize the new emoticon candidate source; emoji alternatives stay explicit
  choices and never enter automatic correction. Engine and emoticon mapping MIT
  notices ship in the APK and are available from About → Obadh Engine.
- Added optional KLIPY GIFs and stickers, separate toolbar controls, compact native
  search, bounded thumbnail recents, recipient/privacy checks and theme-aware
  phone/tablet layouts. Offline sticker packs are no longer bundled. Configured
  local APKs still use KLIPY's testing environment pending production approval.

- Measured default key boxes, glyph sizes and light/dark colors against installed
  Gboard on phone and tablet, portrait and landscape. Fixed tablet split activation,
  removed unrelated tablet alphabet extras and aligned functional keys.
- Added shared fixed/wallpaper/photo themes, key borders, number and language keys,
  keyboard size and automatic/full/split tablet layouts. Photo processing is bounded,
  private and asynchronous; native language picking follows the selected appearance.
- Corrected Bangla personal predictions to use the session C ABI when editor context
  matches, with model-only incognito queries and an isolated real-model regression.
- Kept voice typing out of scope; its toolbar button explains that it is planned.
- Actual touch checks cover language switching, English glide, one-handed and floating
  typing. Both languages pass 24 Gboard geometry/theme configurations each; all 48
  JVM checks and 57 real-editor checks pass. Photo removal handles shrinking theme
  choices without a stale selection crash. Live appearance changes rebuild the cached
  native view; learning uses the editor’s existing context to avoid false word pairs.
- Default Rust builds now target only the ARM64 APK ABI, saving unused build space.
- Aligned Rust and English native builds for 16 KB pages, added packaged-library
  LOAD/RELRO/ZIP safety checks to CI, and updated the AndroidX path dependency.
  The older optional swipe decoder is withheld on incompatible page-size devices.
  Pinning the app's existing NDK restores symbol stripping, reducing the combined
  optimized APK from about 29 MiB to 22 MiB without removing typing features.

- Optional English swipe typing: one native switch and download prompt in setup and
  Gestures, Android background transfer, progress/waiting/error states, cancellation
  and retry, pinned checksum verification and automatic activation. No file import.
- Integrated Bangla correction, loanwords, prediction, personal learning, protected
  spellings, shortcuts and bilingual emoji into the native editor. One settings
  snapshot controls both languages, including pairs, field handling and gestures.
- Model loading, suggestion queries, emoji search and coalesced learning saves run
  away from the input thread. Stale results are generation-checked; deletion uses
  acknowledged commands to the process that owns the data.
- Real-editor emulator regressions cover both languages, fast boundaries, literal
  fields, cursor edits, shortcut/pair switches, emoji and return actions.

### Changed
- Replaced the iOS-inspired app chrome with Material 3 Expressive: Android toolbars,
  dynamic color, standard preference switches and radio dialogs, and Material Symbols.
- Settings now opens a category index. Custom typing controls live under Advanced
  in the overflow menu; build diagnostics are disclosed on demand in About.
- Setup is one page with the two required Android actions and a typing field.
  Removed the animated welcome, completion page and simulated system settings.
- Text shortcuts use a full-screen editor with duplicate validation and unsaved-change
  handling. Deleting clipboard history now asks for confirmation.
- Updated Compose, the Kotlin compiler, AGP and Gradle for the public Expressive
  theme API (`material3:1.5.0-beta01`). Compile SDK is 37.0; target SDK remains 35.

## v0.1.0 — 2026-10-08

Version: `0.1.0` (build `1`). Engine: `0.9.4`. Tag: `v0.1.0` (annotated). First release.

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
- **Tests.** 36 unit tests, against the real artifacts for emoji.

- **Clipboard on the keys.** Long-press X, C or V to cut, copy or paste (all form
  factors; on tablets the key's symbol stays on the downward flick). With nothing
  selected, cut and copy take the whole field; copy restores the cursor. A small icon
  badge pops at the right of the suggestion bar to confirm, only when the action ran.
- **One language switch.** The ribbon's language tool and the number pad's EN/বাং key are gone; swiping the space bar (letters or number pad) is the only way, and the pad's digits follow the language.
- **Gboard-style icons and ribbon animation.** Shift is an outlined arrow, filled when
  on and barred when locked; backspace is the tag-with-x glyph. The ribbon's tools
  switch turns tiles into a chevron while suggestions and tools cross-slide.
- **Typing sound.** Settings → Keyboard → Typing Sound (off by default) plays the system
  key click, with distinct space, return and delete clicks. It follows the phone's volume
  and "Touch sounds" setting.
- **Space-bar trackpad.** Hold space (350 ms), then slide to move the caret one character
  per 9 dp sideways and one line per 22 dp up or down, by grapheme cluster through the host's own arrow handling. Letters dim while
  it is active; releasing does not type a space.
- **Long-press symbols on phones.** Holding a letter key types its second glyph (Bangla
  digits on the top row, then @ # ৳ & * ( ) ' " % / ; :), and holding a symbol key types its
  shifted partner (`,` → `!`, `-` → `_`, Bangla digit → Latin digit). The glyph is hinted in
  the key's top-right. While held, a bubble above the key shows what will be typed; it is
  typed when you lift on the key, and sliding off the key dismisses it with nothing typed.
  X, C and V keep cut, copy and paste only.
- **Key-press callout.** Pressing a letter or symbol raises an iOS-style bubble above the key
  showing the character, flowing into the key through rounded fillets, following the finger
  across keys and clamped at the screen edges. It is drawn on a full-window overlay so the top
  row can pop above the keyboard.
- **Tools switch.** A switch at the left of the suggestion ribbon swaps the suggestions for
  four icons: Clipboard, Numbers, Emoji and Settings. A new field opens on the tools; the
  first key typed moves to suggestions.
- **Clipboard panel.** Everything copied while Obadh is running, newest first (30 items), as
  tappable cards: tap pastes, the cross removes one, Clear removes all. Stored only in private
  app storage; passwords and clips marked sensitive are skipped. Turn it off under Settings →
  Keyboard → Clipboard History; Privacy has Clear Clipboard History.
- **Clipboard pins.** The pin on each card keeps it at the top (outlined in the accent colour). Pins
  never age out of the 30-item history (up to 20), survive the panel's Clear, and are removed only
  by their cross or Privacy → Clear Clipboard History.
- **Text shortcuts.** Settings → Text Shortcuts lets you preset a trigger and a phrase (`@@` → your
  email, `eml`, a phone number, a sign-off). Typing the trigger then space or return replaces it
  with the phrase, in Bangla and English mode and for symbols. Case-sensitive, up to 100, stored
  only on the device.
- **Number pad.** A dialler-style pad in Bangla or Latin digits (EN / বাং switch) with
  `+ - . ,`, space, return and backspace.
- **English / Bangla.** A language tool in the ribbon's tools row (shows বাং or EN) flips between
  transliteration and plain English typing, saved across sessions. The bottom-row globe is gone;
  the system's switcher reaches other keyboards. English types letters as-is with Latin digits,
  and double space gives `. `. The ribbon shows spelling corrections from the device's own spell
  checker (nothing bundled); with no checker enabled in system settings it stays empty.
- The emoji key uses the same smiley icon as the tools row.
- **Comma and full stop on the bottom row.** A comma sits left of the space and a dari (a full stop
  in English) right of it on the phone's letters page, and the return key is as narrow as backspace.
- **Swipe space to switch language.** A quick slide left or right on the space bar flips
  Bangla/English (switch in Gestures). Holding for 350 ms first still starts the trackpad.
- **Language on the space bar.** The bar names the current language between two arrows
  (`◂ বাংলা ▸`, `◂ English ▸`) while the swipe switch is on; the arrows hide when it is off.
- **Swap animation.** Switching language slides the old name out the way the finger went
  and the new one in from the other side (220 ms, clipped to the space bar).
- **Space returns to letters.** Optional switch (on by default): tapping space on the
  numbers or symbols page goes back to the letters. Off keeps the page open.
- **Slide to select, cut or copy.** Hold X or C, then slide left or right: words before
  or after the caret are selected (one per 30 dp, like backspace's swipe), and lifting cuts
  or copies them. Lifting without sliding still takes the whole field; cut and copy now
  act on lift rather than at 420 ms.
- **The caret stays in the field.** The space-bar trackpad and the volume keys no longer send an arrow key
  when the text has no room that way, so they cannot move focus to another view. Up on the first line goes to
  the start of the text and down on the last goes to the end (as on iOS).
- **Roomier phone keys.** Portrait phone rows are 53 dp (was 50) and the safe area under the bottom row is 22 dp (was 14), so the keys sit higher and are a little larger.
- **Double space switch.** Settings → Smart Typing → Double Space for Full Stop turns the quick double-space (`। ` in Bangla, `. ` in English) on or off.
- **Plain-language settings.** Every switch in Settings now has a short description with an example under its title.
- **Volume keys move the cursor.** Settings → Gestures → Volume Keys Move Cursor (off by default): while the
  keyboard is showing, volume up steps the caret forward one character and volume down steps it back; holding
  repeats. The volume panel does not appear while it is on.
- **A switch for every feature.** Settings → Gestures (space-bar trackpad, swipe backspace, hold for
  symbols, hold X/C/V, key preview bubble), Smart Typing (smart fields, auto-capitalise, English
  spelling, pairs, return key actions) and Text Shortcuts (expand shortcuts) can each be turned off. All
  are on by default; a change applies when the next field opens.
- **Haptic strength.** Settings → Keyboard → Haptic Strength: Off, Light, Medium or Strong replaces the
  on/off switch (an old "off" stays off, "on" becomes Medium). It plays a short vibration at that
  amplitude, and now applies to long presses, flicks and the emoji panel too. Adds the normal
  `VIBRATE` permission.
- **Smarter fields.** E-mail, web-address and password fields open in English but you can switch to
  Bangla from the language tool for that field (it resets on the next field and never changes your saved
  language). They also type punctuation literally (no curly quotes, dashes or dari on double space) and
  do not learn words. English mode capitalises sentences, words or characters as the field asks.
- **More field smarts.** The return key shows the field's action as an icon (magnifier, arrow, paper plane, arrow to bar, tick; the app's own label stays text), and on
  a single-line field with no action it finishes the field instead of adding a line. E-mail and web
  fields get a `.com` key. Name and address fields always capitalise words (e-mail, web and password
  fields never do). Brackets and opening quotes type as a pair with the caret inside, a typed closer
  steps over its twin, and backspacing an empty pair removes both. The ribbon hides on the number
  pad in number and phone fields.
- **Field-aware keys.** Number, phone and date fields open on the number pad. E-mail, web-address and
  password fields type in English (your saved language is untouched) with no spelling suggestions;
  the comma key becomes `@` in e-mail fields and `/` in web-address fields.
- **Swipe to delete.** Slide left from backspace (past 24 dp): the words before the caret are
  highlighted, one more per 30 dp, and deleted when you lift. Sliding back shrinks the selection;
  cancelling deletes nothing.

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
- Keyboard drawn at the top of the app card in recents (KI-001, resolved on the test
  phone). The system composites the keyboard into the host app's screenshot from the top
  of its capture area, so a window only as tall as the keys landed at the top of the
  card. The input view is now a full-display-height transparent surface with the keys
  anchored to the bottom; `onComputeInsets` reports the keys' top as the content/visible
  inset and limits touches to the keys' rectangle. Found by diffing the compositor layer
  of another keyboard (1080×2240 surface) against ours (1080×856); no app-side window
  attribute differed.
- Black strip under the keyboard: the navigation-bar strip matches the keys.
- Duplicate Bangla-digit row and duplicate backspace on the extended tablet pages.
- Duplicate engine library packed into every ABI.

### Known
See [KNOWN-ISSUES.md](KNOWN-ISSUES.md), including the unverified touch behaviour.
