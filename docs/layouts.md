# Layouts: phone, tablet, landscape

This document describes the unregistered legacy canvas host. The current Android
native renderer uses measured Gboard-style geometry and split/full layouts; see
[gboard-parity.md](gboard-parity.md) and [heliboard-integration.md](heliboard-integration.md).

The keyboard has one layout engine, `KeyboardLayoutProvider`, that returns rows
of keys with weights. `KeyboardView` turns weights into pixels. Everything below
is data plus a few rules, which is why it is unit-tested without a device.

## Phone

Four rows: the three letter rows, then a command row. The home row is indented
by half a key on each side. The command row is `123`, emoji, space,
return (no globe; see "Language" in text-composition.md). The number page emits Bangla numerals ০–৯ and the punctuation page
carries `৳` and `।`. Latin digits appear on every page while the language is English.

## Tablet families

A tablet's layout family is chosen from the **device's smallest width**
(`smallestScreenWidthDp`), never the live width. Rotating a tablet stretches the
layout it has; it does not hand it another row structure. This is the same rule
obadh-ios uses for iPad (portrait width), for the same reason: a device's family
is a property of the device.

| Family | Smallest width | Rows | Distinguishing keys |
|---|---|---|---|
| Compact | 600–719 dp | 4 | Home row indented with Return at its end; extra `,` and `।` on the bottom letter row |
| Standard | 720–899 dp | 4 | Adds Tab and Caps Lock; wide shifts |
| Extended | 900 dp and up | 5 | A Bangla-numeral number row, plus `[ ] \`, `; '` and `/` |

All tablet families add a hide-keyboard key, taller keys, and per-family margins
and gaps. Boundaries sit in the gaps between real device widths. The number and
symbol pages keep the family's frame (same row count, same command row), so
switching pages never resizes the keyboard. On the extended family the number row
is always present, so its numbers page moves punctuation up rather than repeating
the digits, and the symbols page uses its freed row for currency and marks
(including `₹`). Exactly one backspace and one return exist on every page.

### Weights

Weights are ratios in units of the letter-key width, so a family stretches to any
width and either orientation. They are fitted from the iPad measurements in
obadh-ios, since the structure is shared. They have **not** been re-measured on
Android tablets; see [KNOWN-ISSUES.md](../KNOWN-ISSUES.md#ki-004).

## Landscape

Landscape is its own geometry, not a stretched portrait.

- **Tablets** have per-orientation margins, gaps and row heights. The letter-row
  weights hold in both orientations, but the bottom row does not: landscape gives
  the space bar a larger share and the side keys less. The compact home row is
  less indented with a shorter Return key. The extended number row is 75% of a
  normal row in both orientations.
- **Phones** use shorter rows (38 dp against 53 dp) and a shorter suggestion
  strip, so the app above stays visible, and the key block is capped at 820 dp
  and centred rather than stretched across the whole glass.
- **Insets.** Left and right padding follows the navigation-bar and display-cutout
  insets, so keys avoid a camera cutout or a side navigation bar.
- **Rotation** rebuilds the layout and closes any open emoji panel.

Row heights (58/64/56 dp on tablets in landscape) and the phone cap are
engineering choices, not measurements.

## Secondary glyphs

On a tablet each letter key prints a second glyph in its top-left corner and types
it on a downward flick or a long press. Positions follow the standard tablet
keyboard; content substitutes where a Bangla keyboard makes a strictly better
choice: the top row carries ১২৩৪৫৬৭৮৯০, `d` carries `৳`, and the extended number
row carries the Latin digits, the only place both numeral systems can sit. `?` and
`!` are sentence terminators, so they commit the current word like the dedicated
keys. Phones have no secondary glyphs.

## Reviewing a layout without a tablet

Debug builds include `KeyboardPreviewActivity`, which renders the real
`KeyboardView` as if the device had a given width and scales it to fit the
screen. It changes no device setting and is not in release builds.

```bash
adb shell am start -n org.unmukto.obadh/.debug.KeyboardPreviewActivity \
  --ei sw 1000 --es mode numbers --ez land true
# sw: smallest width in dp; mode: letters|numbers|symbols; land: landscape
```

## Adding a key or a family

Add the key to `Key`, give it a label in `KeyboardView.label`, handle it in
`ObadhInputMethodService.onKey` and `onSearchKey`, and add it to a row. Then
extend `KeyboardLayoutTest`: every row needs one weight per key, every page one
backspace and one return, every command row one space, emoji and hide key.
