# Gboard comparison, 9 October 2026

Reference: installed Google-signed Gboard **18.4.1.985164140-release-arm64-v8a**,
version code 176027078. English US QWERTY and Bangla Bangladesh phonetic are enabled.
Theme is **System Auto**, with key borders. Default Obadh theme uses the same measured
system light/dark surface colors; custom palettes are Obadh-owned designs. Google
code, artwork and APK are not included in the product or repository.

The existing Android 15 ARM64 AVD is resized, rather than downloading tablet images.
Profiles: 393 dp phone (1080×2340 / 440 dpi), 600 dp compact tablet
(1200×1920 / 320 dpi), and 900 dp tablet (1800×2560 / 320 dpi). Additional profiles cover 360 dp and
411 dp phones (1080×2160 / 480 dpi and 1440×3120 / 560 dpi), plus an 800 dp
tablet (1600×2560 / 320 dpi). Each is tested in portrait and landscape, light
and dark: **24 reference captures, 24 English Obadh captures and 24 Bangla Obadh captures**.

`scripts/keyboard-parity.py` uses actual editor taps for both IMEs. It measures solid
key fills with connected components, then isolates the first q glyph below its hint.
It rejects missing IMEs or unsettled theme configuration. Gates compare the first
three letter rows: exact detected key counts, ≤3 dp row/panel position difference,
≤1.5 dp key width/height difference, ≤1 dp glyph width/height difference, and ≤6 RGB
levels per shared keyboard surface. Colors are measured on the keyboard, not on the
host editor. Captures/JSON remain under ignored `build/parity/`.

All 48 Obadh configurations pass, 24 per language. The original three-profile matrix has a
maximum q glyph difference of **0.36 dp**; the expanded phone/tablet matrix is
also within the 1 dp glyph gate. Actual touch regressions also verify composing
Bangla through key taps, globe and space-swipe switching, the long-space picker,
English glide typing of “world”, and one-handed/floating typing in both languages. Representative
light-mode top-row values below are **Obadh / Gboard**, in dp:

| Profile | Orientation | Fill width | Fill height | Row top |
| --- | --- | --- | --- | --- |
| phone | portrait | 34.55 / 34.55 | 40.36 / 40.36 | 601.45 / 600.0 |
| phone | landscape | 70.91 / 70.91 | 34.18 / 34.55 | 233.45 / 232.73 |
| compact-tablet | portrait | 48.0 / 48.0 | 44.0 / 43.5 | 691.0 / 692.0 |
| compact-tablet | landscape | 45.5 / 45.0 | 44.0 / 43.5 | 331.0 / 332.0 |
| tablet | portrait | 46.0 / 45.0 | 44.0 / 43.5 | 1011.0 / 1012.0 |
| tablet | landscape | 45.5 / 45.0 | 44.0 / 43.5 | 631.0 / 632.0 |

Default light: background `#F0F4F9`, keys `#FFFFFF`, functional keys `#E1E3E1`,
text `#1F1F1F`. Dark: background `#1E1F20`, keys `#37393B`, functional `#444746`,
text `#E3E3E3`. Enter uses the blue action capsule. Split layouts keep a single
spacebar and repeat the center boundary letter keys. Explicit full/split settings
remain available. Phones at least 400 dp wide use the measured taller-key resources. Native key
handling, popup keys and gesture machinery are retained.

## Feature scope

| Gboard-style capability | Obadh behavior |
| --- | --- |
| QWERTY, symbols, shift/caps, key preview, number row, language/emoji keys | Native shared renderer; only Bangla and English shipped |
| Light/dark, wallpaper colors, fixed palettes, photo theme, key borders | Shared across keyboard, panels and tools; bounded private photo processing |
| Automatic tablet split, full/split choice, one-handed/floating, size | Native tools and adaptive geometry |
| Correction, spelling, suggestions, learned words | Native English engine; Obadh Bangla C ABI, shared learning/privacy controls |
| Glide typing | Optional verified background download; English only, compatible 4 KB-page devices |
| Space gestures and language switching | Globe/long-space picker plus user-requested space-swipe switching; vertical space cursor movement |
| Emoji, tones, recents, bilingual search and inline suggestions | Native panel with English/Bangla/Roman-Bangla search |
| Clipboard/pins, editing toolbar, undo/redo, cursor navigation | Native tools; excludes private/sensitive collection |
| Text shortcuts and paired punctuation | Shared editor transactions for both languages |
| Voice typing | Intentionally excluded from this phase |
| GIFs/stickers, cloud translation, Google account/AI services | Not represented as working local features; GIF provider evaluation is documented separately |

This is a measured approximation of the observed release, not pixel equality of
every Google screen or a promise to reproduce proprietary services. Custom themes,
large text, number row, nondefault keyboard size and floating/one-handed modes
intentionally alter geometry. Emulated profiles do not certify physical device
fonts, touch-to-photon latency, vendor insets, Android 17 runtime or every host app.
Repeat the harness and interaction checklist on physical devices before store release.
