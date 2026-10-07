# Emoji: suggestions, search, and the data pipeline

The emoji runtime is the most portable part of the iOS codebase: the build
pipeline, the ranking, and the binary formats have no iOS dependency. Android
reuses the **same artifacts byte for byte** (`scripts/sync-models.sh` copies them
from obadh-ios) and ports only the readers. The generation pipeline and ranking
rationale are documented in
[obadh-ios/docs/emoji.md](../../obadh-ios/docs/emoji.md).

## Artifacts

| File | Format | Used for |
|---|---|---|
| `emoji-bn.bin` (~110 KB) | `OBEMOJIBN1`, sorted key → up to 3 emoji | Inline suggestions, exact binary search |
| `emoji.bin` (~1 MB) | `OBEMOJI1`, items + token postings | The panel catalog and English search |
| `emoji-bn-search.bin` (~130 KB) | `OBEMOJIBN1`, token → ≤16 ranked emoji | Bangla search |

All three are read in pure Kotlin (`emoji/`), memory-mapped from the models
directory the installer populates.

## Inline suggestions

When a Bangla word is being composed, up to three emoji appear in the ribbon's
third slot (the top two text candidates always survive). The lookup is a binary
search over the mapped bytes, a miss allocates nothing but the needle, and the
key is normalized byte-for-byte like the generator (NFC, strip ZWNJ/ZWJ, trim,
never strip matras), so it is safe on the per-keystroke path.

Tapping one **replaces** the composed word, because the typed text was the
emoji's query, and the discarded query is not committed to autosuggest learning.
The emoji for a word that just committed stay on the ribbon after the space until
the next letter, so the space does not snatch away the suggestion the user was
reaching for. The remembered skin tone is applied from a key-value lookup; the
catalog is not loaded for this.

## The panel

The emoji key opens a custom-drawn panel (`EmojiPanelView`): a horizontally
flinging four-row grid of sections with recents first, a category bar that tracks
the scroll position, ⌫ with the native-like repeat curve, and a search button.
Long-pressing an emoji that has skin-tone variants opens a popup; dragging picks
a tone, which is remembered per emoji and used everywhere (grid, search, ribbon).

- **Lazy catalog.** The ~1 MB catalog is decoded on the worker thread the first
  time the panel opens and cached. The typing path never touches it.
- **Device font.** Android versions lag Unicode, so every emoji is filtered with
  `Paint.hasGlyph`; one the device cannot draw would render as a box.
- **Cell size** scales with the grid's row height, so four rows fit the short
  panel of a phone in landscape.
- **Recents** are pure most-recently-used order, one screenful. A time-decayed
  use count (half-life two weeks) decides only what *leaves* a full list, so a
  weekly favourite survives a burst of one-off novelty (`EmojiRecentStore`).

## Search

The search button turns the panel into a field and one row of results, with the
letter keyboard beneath it. English search is the catalog's token index with
exact, prefix, substring and edit-distance matching and phrase boosts. Every
term of a multi-word query must match, skin-tone variants are hidden unless the
query mentions skin or tone, and results cap at 40.

Bangla search types Roman and transliterates it into the field with the engine
(`tq`/`qq` included), then searches `emoji-bn-search.bin`: exact and prefix, AND
of terms, with a grapheme edit-distance fuzzy fallback against the closed keyword
vocabulary. That index loads only when Bangla search is first used. The in-bar
`EN`/`বাং` chip toggles the language for the session, and Settings › Search
Language sets which one search opens in.

## Verification

`EmojiStoresTest` runs against the real artifacts: curated words resolve to
their emoji, misses and normalization behave exactly, no word returns more than
three emoji or a skin-tone variant, the catalog loads and searches, skin tones
group behind the base emoji, Bangla search covers exact, prefix, miss and fuzzy,
and recents evict by decayed score. **The panel, search mode and gestures have
not been exercised on a device**; see [KNOWN-ISSUES.md](../KNOWN-ISSUES.md#ki-002).
