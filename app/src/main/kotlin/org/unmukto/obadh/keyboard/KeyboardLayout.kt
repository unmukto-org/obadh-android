package org.unmukto.obadh.keyboard

enum class KeyboardMode { LETTERS, NUMBERS, SYMBOLS }

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
}

data class KeyboardRow(
    val keys: List<Key>,
    val weights: List<Double> = keys.map { it.weight },
    /** Fraction of a unit key left empty on each side (the indented home row). */
    val sideFlex: Double = 0.0,
)

/**
 * Letters follow QWERTY (Roman typing); numbers are Bangla numerals only, with ৳
 * and দাঁড়ি on the punctuation pages. There is no English mode: the globe leaves.
 */
object KeyboardLayoutProvider {
    private val bnDigits = listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০")
    private fun lit(vararg v: String) = v.map { Key.Symbol(it) }
    private val dari = Key.Symbol("।", terminator = true)
    private val punctuationTail = listOf(dari, Key.Symbol("."), Key.Symbol(","), Key.Symbol("?", terminator = true), Key.Symbol("!", terminator = true))

    private fun commandRow(mode: Key, withGlobe: Boolean, withEmoji: Boolean = false) = KeyboardRow(
        buildList {
            add(mode)
            if (withEmoji) add(Key.Emoji)
            if (withGlobe) add(Key.Globe)
            add(Key.Space)
            add(Key.Return)
        },
    )

    fun rows(mode: KeyboardMode, includesGlobeKey: Boolean = true): List<KeyboardRow> = when (mode) {
        KeyboardMode.LETTERS -> listOf(
            KeyboardRow("qwertyuiop".map { Key.Character(it.toString()) }),
            KeyboardRow("asdfghjkl".map { Key.Character(it.toString()) }, sideFlex = 0.5),
            KeyboardRow(
                listOf(Key.Shift) + "zxcvbnm".map { Key.Character(it.toString()) } + Key.Backspace,
                weights = listOf(1.5) + List(7) { 1.0 } + 1.5,
            ),
            commandRow(Key.ModeSwitch("123", KeyboardMode.NUMBERS), includesGlobeKey, withEmoji = true),
        )
        KeyboardMode.NUMBERS -> listOf(
            KeyboardRow(bnDigits.map { Key.Symbol(it) }),
            KeyboardRow(lit("-", "/", ":", ";", "(", ")", "৳", "'", "@", "\"")),
            KeyboardRow(
                listOf<Key>(Key.ModeSwitch("#+=", KeyboardMode.SYMBOLS)) + punctuationTail + Key.Backspace,
                weights = listOf(1.5) + List(5) { 1.2 } + 1.5,
            ),
            commandRow(Key.ModeSwitch("ABC", KeyboardMode.LETTERS), includesGlobeKey),
        )
        KeyboardMode.SYMBOLS -> listOf(
            KeyboardRow(lit("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")),
            KeyboardRow(lit("_", "\\", "|", "~", "<", ">", "&", "$", "€", "£")),
            KeyboardRow(
                listOf<Key>(Key.ModeSwitch("123", KeyboardMode.NUMBERS)) + punctuationTail + Key.Backspace,
                weights = listOf(1.5) + List(5) { 1.2 } + 1.5,
            ),
            commandRow(Key.ModeSwitch("ABC", KeyboardMode.LETTERS), includesGlobeKey),
        )
    }
}
