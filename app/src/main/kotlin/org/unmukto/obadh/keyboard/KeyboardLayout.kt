package org.unmukto.obadh.keyboard

/** NUMPAD_BN and NUMPAD_EN are the number pad in Bangla and Latin digits (reached from the ribbon's tools). */
enum class KeyboardMode { LETTERS, NUMBERS, SYMBOLS, NUMPAD_BN, NUMPAD_EN }

sealed interface Key {
    val weight: Double

    data class Character(val value: String) : Key { override val weight = 1.0 }

    /** [terminator] symbols (। ? !) commit any active word and mark a sentence boundary. */
    data class Symbol(val label: String, val output: String = label, val terminator: Boolean = false) : Key {
        override val weight = 1.0
    }

    data object Shift : Key { override val weight = 1.35 }
    data object Backspace : Key { override val weight = 1.35 }
    data class ModeSwitch(val label: String, val target: KeyboardMode) : Key { override val weight = 1.25 }
    data object Globe : Key { override val weight = 1.25 }
    data object Emoji : Key { override val weight = 1.25 }
    data object Space : Key { override val weight = 5.0 }
    data object Return : Key { override val weight = 2.25 }

    /** Tablet-only keys. */
    data object Tab : Key { override val weight = 1.3 }
    data object CapsLock : Key { override val weight = 1.6 }
    data object HideKeyboard : Key { override val weight = 1.6 }
}

/**
 * One row. [weights] are in units of the letter-key width, so a tablet layout is a set of
 * ratios that stretch to any width. [leadingFlex]/[trailingFlex] are empty space, in the same
 * units, for the indented home row.
 */
data class KeyboardRow(
    val keys: List<Key>,
    val weights: List<Double> = keys.map { it.weight },
    val leadingFlex: Double = 0.0,
    val trailingFlex: Double = 0.0,
    /** This row's height as a fraction of a normal key row (the extended number row is shorter). */
    val heightFactor: Double = 1.0,
) {
    init { require(weights.size == keys.size) { "one weight per key" } }
}

/**
 * The tablet layout family. Chosen from the DEVICE's smallest width (its portrait width), never
 * the live width: rotating a tablet stretches the layout it has, it does not hand it another
 * row structure. That is the same rule obadh-ios applies to iPad.
 */
enum class TabletFamily(
    /** Edge margin and key gap, in dp, portrait then landscape (landscape is its own geometry). */
    private val marginPortrait: Float, private val marginLandscape: Float,
    private val gapPortrait: Float, private val gapLandscape: Float,
    /** Height of one key row, in dp. */
    private val rowPortrait: Float, private val rowLandscape: Float,
) {
    /** 7-8 inch. Four rows, no tab or caps lock, indented home row. */
    COMPACT(6f, 7f, 12f, 14f, 56f, 58f),
    /** 10-11 inch. Four rows plus tab and caps lock. */
    STANDARD(9f, 15f, 10f, 14f, 62f, 64f),
    /** 12 inch and up. Five rows: a real number row appears. */
    EXTENDED(3.5f, 5f, 7f, 10f, 58f, 56f);

    fun marginDp(landscape: Boolean) = if (landscape) marginLandscape else marginPortrait
    fun gapDp(landscape: Boolean) = if (landscape) gapLandscape else gapPortrait
    fun rowHeightDp(landscape: Boolean) = if (landscape) rowLandscape else rowPortrait

    companion object {
        /** Boundaries sit in the gaps between real device widths (600, 720, 800, 960...). */
        fun forSmallestWidthDp(dp: Int): TabletFamily? = when {
            dp < 600 -> null
            dp < 720 -> COMPACT
            dp < 900 -> STANDARD
            else -> EXTENDED
        }
    }
}

/**
 * Letters follow QWERTY (Roman typing); numbers are Bangla numerals only, with ৳ and দাঁড়ি on
 * the punctuation pages. Bangla is the only language: the globe opens the system picker.
 */
object KeyboardLayoutProvider {
    private val bnDigits = listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")
    private fun lit(vararg v: String) = v.map { Key.Symbol(it) }
    private val dari = Key.Symbol("।", terminator = true)
    private val punctuationTail = listOf(dari, Key.Symbol("."), Key.Symbol(","), Key.Symbol("?", terminator = true), Key.Symbol("!", terminator = true))
    private fun chars(s: String) = s.map { Key.Character(it.toString()) }

    private val numbersRow2 = lit("-", "/", ":", ";", "(", ")", "৳", "'", "@", "\"")
    private val symbolsRow1 = lit("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
    private val symbolsRow2 = lit("_", "\\", "|", "~", "<", ">", "&", "$", "€", "£")

    // ------------------------------------------------------------------ phone

    private fun commandRow(mode: Key, withGlobe: Boolean, withEmoji: Boolean = false) = KeyboardRow(
        buildList {
            add(mode)
            if (withEmoji) add(Key.Emoji)
            if (withGlobe) add(Key.Globe)
            add(Key.Space)
            add(Key.Return)
        },
    )

    fun rows(
        mode: KeyboardMode,
        includesGlobeKey: Boolean = true,
        family: TabletFamily? = null,
        landscape: Boolean = false,
    ): List<KeyboardRow> =
        when {
            mode == KeyboardMode.NUMPAD_BN || mode == KeyboardMode.NUMPAD_EN -> numpadRows(mode)
            family != null -> tabletRows(mode, family, includesGlobeKey, landscape)
            else -> phoneRows(mode, includesGlobeKey, landscape)
        }

    /**
     * A dialler-style pad: digits in the middle, operators and punctuation down the left,
     * backspace and return on the right. The switch key flips between Bangla and Latin digits.
     */
    private fun numpadRows(mode: KeyboardMode): List<KeyboardRow> {
        val bangla = mode == KeyboardMode.NUMPAD_BN
        val d = if (bangla) listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯") else ('0'..'9').map { it.toString() }
        fun n(i: Int) = Key.Symbol(d[i])
        val other = if (bangla) Key.ModeSwitch("EN", KeyboardMode.NUMPAD_EN) else Key.ModeSwitch("বাং", KeyboardMode.NUMPAD_BN)
        return listOf(
            KeyboardRow(listOf(Key.Symbol("+"), n(1), n(2), n(3), Key.Backspace), weights = listOf(1.0, 1.0, 1.0, 1.0, 1.0)),
            KeyboardRow(listOf(Key.Symbol("-"), n(4), n(5), n(6), Key.Symbol(",")), weights = listOf(1.0, 1.0, 1.0, 1.0, 1.0)),
            KeyboardRow(listOf(Key.Symbol("."), n(7), n(8), n(9), Key.Return), weights = listOf(1.0, 1.0, 1.0, 1.0, 1.0)),
            KeyboardRow(
                listOf(Key.ModeSwitch("ABC", KeyboardMode.LETTERS), other, n(0), Key.Space),
                weights = listOf(1.0, 1.0, 1.0, 2.0),
            ),
        )
    }

    private fun phoneRows(mode: KeyboardMode, includesGlobeKey: Boolean, landscape: Boolean): List<KeyboardRow> = when (mode) {
        KeyboardMode.NUMPAD_BN, KeyboardMode.NUMPAD_EN -> numpadRows(mode)
        KeyboardMode.LETTERS -> listOf(
            KeyboardRow(chars("qwertyuiop")),
            KeyboardRow(chars("asdfghjkl"), leadingFlex = 0.5, trailingFlex = 0.5),
            KeyboardRow(
                listOf(Key.Shift) + chars("zxcvbnm") + Key.Backspace,
                weights = listOf(1.5) + List(7) { 1.0 } + 1.5,
            ),
            commandRow(Key.ModeSwitch("123", KeyboardMode.NUMBERS), includesGlobeKey, withEmoji = true),
        )
        KeyboardMode.NUMBERS -> listOf(
            KeyboardRow(bnDigits.map { Key.Symbol(it) }),
            KeyboardRow(numbersRow2),
            KeyboardRow(
                listOf<Key>(Key.ModeSwitch("#+=", KeyboardMode.SYMBOLS)) + punctuationTail + Key.Backspace,
                weights = listOf(1.5) + List(5) { 1.2 } + 1.5,
            ),
            commandRow(Key.ModeSwitch("ABC", KeyboardMode.LETTERS), includesGlobeKey),
        )
        KeyboardMode.SYMBOLS -> listOf(
            KeyboardRow(symbolsRow1),
            KeyboardRow(symbolsRow2),
            KeyboardRow(
                listOf<Key>(Key.ModeSwitch("123", KeyboardMode.NUMBERS)) + punctuationTail + Key.Backspace,
                weights = listOf(1.5) + List(5) { 1.2 } + 1.5,
            ),
            commandRow(Key.ModeSwitch("ABC", KeyboardMode.LETTERS), includesGlobeKey),
        )
    }

    // ----------------------------------------------------------------- tablet
    //
    // Weights are fitted ratios in units of the letter-key width (from the iPad measurements in
    // obadh-ios, which the structure shares). They stretch to any width, in either orientation.

    private object W {
        object Compact {
            const val BACKSPACE = 1.239; const val RETURN = 1.972; const val LEFT_SHIFT = 1.0
            const val RIGHT_SHIFT = 1.239; const val HOME_INDENT = 0.486
            const val COMMAND = 1.046; const val SPACE = 5.835; const val WIDE = 1.679
        }
        object Standard {
            const val TAB = 1.292; const val BACKSPACE = 1.292; const val CAPS = 1.648
            const val RETURN = 2.120; const val LEFT_SHIFT = 2.179; const val RIGHT_SHIFT = 1.589
            const val COMMAND = 1.067; const val SPACE = 7.284; const val MODE_RIGHT = 1.589; const val HIDE = 1.594
        }
        /**
         * Landscape is not a stretched portrait: the letter-row weights hold in both orientations
         * (they reproduce within 0.01), but the command row does not. Landscape gives the space bar
         * more and the side keys less, and the compact home row is less indented with a shorter
         * return key. Fitted from the iPad landscape measurements in obadh-ios.
         */
        object Landscape {
            const val COMPACT_COMMAND = 1.021; const val COMPACT_SPACE = 5.770; const val COMPACT_WIDE = 1.612
            const val STANDARD_COMMAND = 1.007; const val STANDARD_SPACE = 7.617; const val STANDARD_WIDE = 1.503
            const val COMPACT_HOME_INDENT = 0.466; const val COMPACT_RETURN = 1.931
        }
        object Extended {
            const val TAB = 1.601; const val BACKSPACE = 1.601; const val CAPS = 1.853
            const val RETURN = 1.853; const val SHIFT = 2.410
            const val COMMAND = 1.474; const val SPACE = 6.523; const val MODE_RIGHT = 2.276; const val HIDE = 2.272
        }
    }

    /** The command row, in a tablet's order: globe, mode switch, emoji, space, mode switch, dismiss. */
    private fun tabletCommandRow(
        modeLabel: String, mode: KeyboardMode, family: TabletFamily, withGlobe: Boolean, landscape: Boolean,
    ): KeyboardRow {
        val (narrow, space, modeRight, hide) = when (family) {
            TabletFamily.COMPACT -> if (landscape) {
                listOf(W.Landscape.COMPACT_COMMAND, W.Landscape.COMPACT_SPACE, W.Landscape.COMPACT_WIDE, W.Landscape.COMPACT_WIDE)
            } else listOf(W.Compact.COMMAND, W.Compact.SPACE, W.Compact.WIDE, W.Compact.WIDE)
            TabletFamily.STANDARD -> if (landscape) {
                listOf(W.Landscape.STANDARD_COMMAND, W.Landscape.STANDARD_SPACE, W.Landscape.STANDARD_WIDE, W.Landscape.STANDARD_WIDE)
            } else listOf(W.Standard.COMMAND, W.Standard.SPACE, W.Standard.MODE_RIGHT, W.Standard.HIDE)
            // The 13-inch command row measures the same in both orientations.
            TabletFamily.EXTENDED -> listOf(W.Extended.COMMAND, W.Extended.SPACE, W.Extended.MODE_RIGHT, W.Extended.HIDE)
        }
        val keys = ArrayList<Key>()
        val weights = ArrayList<Double>()
        if (withGlobe) { keys += Key.Globe; weights += narrow }
        keys += Key.ModeSwitch(modeLabel, mode); weights += narrow
        keys += Key.Emoji; weights += narrow
        keys += Key.Space; weights += space
        keys += Key.ModeSwitch(modeLabel, mode); weights += modeRight
        keys += Key.HideKeyboard; weights += hide
        return KeyboardRow(keys, weights)
    }

    /** The two punctuation keys at the end of the bottom letter row; দাঁড়ি takes the period's place. */
    private val rowThreeTail: List<Key> = listOf(Key.Symbol(","), dari)

    /** The extended number row: Bangla numerals, since Obadh has no Latin digits. */
    private fun numberRow(): KeyboardRow = KeyboardRow(
        listOf<Key>(Key.Symbol("`")) + bnDigits.map { Key.Symbol(it) } + Key.Symbol("-") + Key.Symbol("=") + Key.Backspace,
        weights = List(13) { 1.0 } + W.Extended.BACKSPACE,
        heightFactor = NUMBER_ROW_HEIGHT,
    )

    /** The 13-inch number row is shorter than the letter rows (45.5pt against 61 portrait, 59 against 79 landscape). */
    const val NUMBER_ROW_HEIGHT = 0.75

    /** Currency and common marks: the extended symbols page's second row (₹ for Bangla readers). */
    private val currencyAndMarks = lit("€", "£", "¥", "₹", "¢", "©", "®", "™", "°", "•")

    private fun tabletRows(mode: KeyboardMode, family: TabletFamily, withGlobe: Boolean, landscape: Boolean): List<KeyboardRow> {
        // The extended family always shows the Bangla-numeral row on top, so its numbers page must
        // not repeat the digits: it moves everything up a row and uses the freed row for symbols.
        val extended = family == TabletFamily.EXTENDED
        return when (mode) {
            KeyboardMode.NUMPAD_BN, KeyboardMode.NUMPAD_EN -> numpadRows(mode)
            KeyboardMode.LETTERS -> tabletLetterRows(family, withGlobe, landscape)
            KeyboardMode.NUMBERS -> tabletSymbolRows(
                family, withGlobe, landscape, modeLabel = "#+=", target = KeyboardMode.SYMBOLS,
                first = if (extended) numbersRow2 else bnDigits.map { Key.Symbol(it) },
                second = if (extended) symbolsRow1 else numbersRow2,
                third = punctuationTail,
            )
            KeyboardMode.SYMBOLS -> tabletSymbolRows(
                family, withGlobe, landscape, modeLabel = "123", target = KeyboardMode.NUMBERS,
                first = if (extended) symbolsRow2 else symbolsRow1,
                second = if (extended) currencyAndMarks else symbolsRow2,
                third = punctuationTail,
            )
        }
    }

    private fun tabletLetterRows(family: TabletFamily, withGlobe: Boolean, landscape: Boolean): List<KeyboardRow> {
        val top = chars("qwertyuiop")
        val home = chars("asdfghjkl")
        val lower = chars("zxcvbnm")
        val command = tabletCommandRow("?123", KeyboardMode.NUMBERS, family, withGlobe, landscape)
        return when (family) {
            TabletFamily.COMPACT -> listOf(
                KeyboardRow(top + Key.Backspace, List(10) { 1.0 } + W.Compact.BACKSPACE),
                KeyboardRow(
                    home + Key.Return,
                    List(9) { 1.0 } + (if (landscape) W.Landscape.COMPACT_RETURN else W.Compact.RETURN),
                    leadingFlex = if (landscape) W.Landscape.COMPACT_HOME_INDENT else W.Compact.HOME_INDENT,
                ),
                KeyboardRow(
                    listOf<Key>(Key.Shift) + lower + rowThreeTail + Key.Shift,
                    listOf(W.Compact.LEFT_SHIFT) + List(9) { 1.0 } + W.Compact.RIGHT_SHIFT,
                ),
                command,
            )
            TabletFamily.STANDARD -> listOf(
                KeyboardRow(listOf<Key>(Key.Tab) + top + Key.Backspace, listOf(W.Standard.TAB) + List(10) { 1.0 } + W.Standard.BACKSPACE),
                KeyboardRow(listOf<Key>(Key.CapsLock) + home + Key.Return, listOf(W.Standard.CAPS) + List(9) { 1.0 } + W.Standard.RETURN),
                KeyboardRow(
                    listOf<Key>(Key.Shift) + lower + rowThreeTail + Key.Shift,
                    listOf(W.Standard.LEFT_SHIFT) + List(9) { 1.0 } + W.Standard.RIGHT_SHIFT,
                ),
                command,
            )
            TabletFamily.EXTENDED -> listOf(
                numberRow(),
                KeyboardRow(
                    listOf<Key>(Key.Tab) + top + lit("[", "]", "\\"),
                    listOf(W.Extended.TAB) + List(13) { 1.0 },
                ),
                KeyboardRow(
                    listOf<Key>(Key.CapsLock) + home + lit(";", "'") + Key.Return,
                    listOf(W.Extended.CAPS) + List(11) { 1.0 } + W.Extended.RETURN,
                ),
                KeyboardRow(
                    listOf<Key>(Key.Shift) + lower + rowThreeTail + Key.Symbol("/") + Key.Shift,
                    listOf(W.Extended.SHIFT) + List(10) { 1.0 } + W.Extended.SHIFT,
                ),
                command,
            )
        }
    }

    /**
     * Number and symbol pages keep the family's frame, the same row count and the same command
     * row, so switching pages never resizes the keyboard.
     */
    private fun tabletSymbolRows(
        family: TabletFamily, withGlobe: Boolean, landscape: Boolean, modeLabel: String, target: KeyboardMode,
        first: List<Key>, second: List<Key>, third: List<Key>,
    ): List<KeyboardRow> {
        val compact = family == TabletFamily.COMPACT
        val edge = when (family) { TabletFamily.COMPACT -> W.Compact.BACKSPACE; TabletFamily.STANDARD -> W.Standard.BACKSPACE; else -> W.Extended.BACKSPACE }
        val caps = when (family) { TabletFamily.COMPACT -> W.Compact.BACKSPACE; TabletFamily.STANDARD -> W.Standard.CAPS; else -> W.Extended.CAPS }
        val ret = when (family) { TabletFamily.COMPACT -> W.Compact.RETURN; TabletFamily.STANDARD -> W.Standard.RETURN; else -> W.Extended.RETURN }
        val shiftL = when (family) { TabletFamily.COMPACT -> W.Compact.LEFT_SHIFT; TabletFamily.STANDARD -> W.Standard.LEFT_SHIFT; else -> W.Extended.SHIFT }
        val shiftR = when (family) { TabletFamily.COMPACT -> W.Compact.RIGHT_SHIFT; TabletFamily.STANDARD -> W.Standard.RIGHT_SHIFT; else -> W.Extended.SHIFT }

        val rows = ArrayList<KeyboardRow>()
        // The extended number row already carries the page's backspace; a second one would
        // be a duplicate key.
        val hasNumberRow = family == TabletFamily.EXTENDED
        if (hasNumberRow) rows += numberRow()
        rows += KeyboardRow(
            (if (compact) emptyList() else listOf<Key>(Key.Tab)) + first + (if (hasNumberRow) emptyList() else listOf(Key.Backspace)),
            (if (compact) emptyList() else listOf(edge)) + List(first.size) { 1.0 } + (if (hasNumberRow) emptyList() else listOf(edge)),
        )
        rows += KeyboardRow(
            (if (compact) emptyList() else listOf<Key>(Key.CapsLock)) + second + Key.Return,
            (if (compact) emptyList() else listOf(caps)) + List(second.size) { 1.0 } + ret,
        )
        rows += KeyboardRow(
            listOf<Key>(Key.ModeSwitch(modeLabel, target)) + third + Key.ModeSwitch(modeLabel, target),
            listOf(shiftL) + List(third.size) { 1.0 } + shiftR,
        )
        rows += tabletCommandRow("ABC", KeyboardMode.LETTERS, family, withGlobe, landscape)
        return rows
    }

    // ----------------------------------------------------- secondary (flick-down) labels
    //
    // A tablet letter key prints a second glyph in its top-left and emits it when the key is
    // flicked downward or held. Positions follow the standard tablet keyboard; content
    // substitutes where a Bangla keyboard makes a strictly better choice: the digit row is
    // ১২৩৪৫৬৭৮৯০ (Obadh has no Latin digits on the main pages) and d carries ৳ where English
    // carries $. Everything else is kept where muscle memory expects it.

    private val secondaryByCharacter = mapOf(
        "q" to "১", "w" to "২", "e" to "৩", "r" to "৪", "t" to "৫",
        "y" to "৬", "u" to "৭", "i" to "৮", "o" to "৯", "p" to "০",
        "a" to "@", "s" to "#", "d" to "৳", "f" to "&", "g" to "*",
        "h" to "(", "j" to ")", "k" to "'", "l" to "\"",
        "z" to "%", "x" to "-", "c" to "+", "v" to "=", "b" to "/", "n" to ";", "m" to ":",
    )

    private val secondaryBySymbolOutput = mapOf(
        "," to "!", "।" to "?", "/" to "\\", "`" to "~", "-" to "_", "=" to "+",
        "[" to "{", "]" to "}", "\\" to "|", ";" to ":", "'" to "\"",
        // The extended number row: Latin digits, the only place both numeral systems can sit.
        "১" to "1", "২" to "2", "৩" to "3", "৪" to "4", "৫" to "5",
        "৬" to "6", "৭" to "7", "৮" to "8", "৯" to "9", "০" to "0",
    )

    /** The long-press (and, on tablets, flick-down) glyph for [key], or null. */
    fun secondaryFor(key: Key): Key.Symbol? = when (key) {
        is Key.Character -> secondaryByCharacter[key.value]?.let { Key.Symbol(it) }
        is Key.Symbol -> secondaryBySymbolOutput[key.output]?.let { label ->
            if (label == "?" || label == "!") Key.Symbol(label, terminator = true) else Key.Symbol(label)
        }
        else -> null
    }
}
