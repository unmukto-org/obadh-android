package org.unmukto.obadh

import org.junit.Assert.*
import org.junit.Test
import org.unmukto.obadh.keyboard.*

class KeyboardLayoutTest {
    private fun rows(mode: KeyboardMode, family: TabletFamily?, landscape: Boolean = false) =
        KeyboardLayoutProvider.rows(mode, true, family, landscape)

    @Test fun familyIsChosenFromTheDevicesSmallestWidth() {
        assertNull(TabletFamily.forSmallestWidthDp(411))
        assertNull(TabletFamily.forSmallestWidthDp(599))
        assertEquals(TabletFamily.COMPACT, TabletFamily.forSmallestWidthDp(600))
        assertEquals(TabletFamily.COMPACT, TabletFamily.forSmallestWidthDp(719))
        assertEquals(TabletFamily.STANDARD, TabletFamily.forSmallestWidthDp(720))
        assertEquals(TabletFamily.STANDARD, TabletFamily.forSmallestWidthDp(800))
        assertEquals(TabletFamily.EXTENDED, TabletFamily.forSmallestWidthDp(900))
        assertEquals(TabletFamily.EXTENDED, TabletFamily.forSmallestWidthDp(1000))
    }

    @Test fun everyRowHasOneWeightPerKey() {
        for (family in listOf(null) + TabletFamily.entries) for (mode in KeyboardMode.entries) {
            for (row in rows(mode, family)) {
                assertEquals("$family $mode", row.keys.size, row.weights.size)
                assertTrue(row.weights.all { it > 0 })
            }
        }
    }

    @Test fun rowStructureQuantisesByFamily() {
        assertEquals(4, rows(KeyboardMode.LETTERS, null).size)
        assertEquals(4, rows(KeyboardMode.LETTERS, TabletFamily.COMPACT).size)
        assertEquals(4, rows(KeyboardMode.LETTERS, TabletFamily.STANDARD).size)
        assertEquals(5, rows(KeyboardMode.LETTERS, TabletFamily.EXTENDED).size)
    }

    @Test fun tabAndCapsLockAppearOnlyWhereTheFamilyHasThem() {
        fun has(family: TabletFamily, key: Key) = rows(KeyboardMode.LETTERS, family).any { key in it.keys }
        assertFalse(has(TabletFamily.COMPACT, Key.Tab)); assertFalse(has(TabletFamily.COMPACT, Key.CapsLock))
        assertTrue(has(TabletFamily.STANDARD, Key.Tab)); assertTrue(has(TabletFamily.STANDARD, Key.CapsLock))
        assertTrue(has(TabletFamily.EXTENDED, Key.Tab)); assertTrue(has(TabletFamily.EXTENDED, Key.CapsLock))
        // Phones never get tablet keys.
        assertTrue(rows(KeyboardMode.LETTERS, null).none { r -> r.keys.any { it == Key.Tab || it == Key.HideKeyboard } })
    }

    @Test fun compactIndentsTheHomeRowWithReturnOnTheEnd() {
        val home = rows(KeyboardMode.LETTERS, TabletFamily.COMPACT)[1]
        assertTrue(home.leadingFlex > 0)
        assertEquals(Key.Return, home.keys.last())
    }

    @Test fun extendedAddsABanglaNumeralRowAndPageChangesNeverResizeTheKeyboard() {
        val numberRow = rows(KeyboardMode.LETTERS, TabletFamily.EXTENDED).first()
        assertEquals(14, numberRow.keys.size)
        assertEquals(Key.Symbol("১"), numberRow.keys[1])
        assertEquals(Key.Backspace, numberRow.keys.last())
        for (family in TabletFamily.entries) {
            val letters = rows(KeyboardMode.LETTERS, family).size
            assertEquals("$family numbers", letters, rows(KeyboardMode.NUMBERS, family).size)
            assertEquals("$family symbols", letters, rows(KeyboardMode.SYMBOLS, family).size)
        }
    }

    @Test fun extendedNumbersPageDoesNotRepeatTheDigitRowItAlreadyHas() {
        val digits = (1..10).map { Key.Symbol("১২৩৪৫৬৭৮৯০"[it - 1].toString()) }
        for (mode in listOf(KeyboardMode.NUMBERS, KeyboardMode.SYMBOLS)) {
            val page = rows(mode, TabletFamily.EXTENDED)
            // Row 0 is the number row; no other row may be a second copy of the digits.
            assertTrue(page.drop(1).none { r -> r.keys.filter { it in digits }.size >= 5 })
        }
        // Smaller families have no number row, so their numbers page keeps the digits.
        assertTrue(rows(KeyboardMode.NUMBERS, TabletFamily.STANDARD).any { r -> r.keys.count { it in digits } >= 10 })
    }

    @Test fun everyPageHasExactlyOneBackspaceAndOneReturn() {
        for (family in listOf(null) + TabletFamily.entries) for (mode in KeyboardMode.entries) {
            val keys = rows(mode, family).flatMap { it.keys }
            assertEquals("$family $mode backspace", 1, keys.count { it == Key.Backspace })
            assertEquals("$family $mode return", 1, keys.count { it == Key.Return })
        }
    }

    @Test fun numberPadHasEveryDigitOnceAndSwitchesNumerals() {
        for (family in listOf(null) + TabletFamily.entries) {
            val bn = rows(KeyboardMode.NUMPAD_BN, family).flatMap { it.keys }.filterIsInstance<Key.Symbol>().map { it.label }
            val en = rows(KeyboardMode.NUMPAD_EN, family).flatMap { it.keys }.filterIsInstance<Key.Symbol>().map { it.label }
            assertEquals(listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"), bn.filter { it in "০১২৩৪৫৬৭৮৯".map(Char::toString) }.sorted())
            assertEquals(('0'..'9').map { it.toString() }, en.filter { it.length == 1 && it[0].isDigit() }.sorted())
            val switchBn = rows(KeyboardMode.NUMPAD_BN, family).flatMap { it.keys }.filterIsInstance<Key.ModeSwitch>()
            assertTrue(switchBn.any { it.target == KeyboardMode.NUMPAD_EN } && switchBn.any { it.target == KeyboardMode.LETTERS })
            assertEquals(1, rows(KeyboardMode.NUMPAD_BN, family).flatMap { it.keys }.count { it == Key.Space })
        }
    }

    @Test fun everyTabletCommandRowHasOneSpaceEmojiAndHideKey() {
        for (family in TabletFamily.entries) for (mode in KeyboardMode.entries.filter { it != KeyboardMode.NUMPAD_BN && it != KeyboardMode.NUMPAD_EN }) {
            val command = rows(mode, family).last()
            assertEquals(1, command.keys.count { it == Key.Space })
            assertEquals(1, command.keys.count { it == Key.Emoji })
            assertEquals(1, command.keys.count { it == Key.HideKeyboard })
            assertEquals(Key.Globe, command.keys.first())
            // Space is the widest key in the row.
            assertEquals(command.weights.max(), command.weights[command.keys.indexOf(Key.Space)], 0.0)
        }
    }

    @Test fun phoneLayoutIsUnchanged() {
        val letters = rows(KeyboardMode.LETTERS, null)
        assertEquals(10, letters[0].keys.size)
        assertEquals(9, letters[1].keys.size)
        assertEquals(0.5, letters[1].leadingFlex, 0.0)
        // Comma left of the space, dari right of it, and a return as narrow as backspace.
        assertEquals(
            listOf(Key.ModeSwitch("123", KeyboardMode.NUMBERS), Key.Emoji, Key.Globe, Key.Symbol(","), Key.Space, Key.Symbol("।", terminator = true), Key.Return),
            letters[3].keys,
        )
        assertEquals(Key.Backspace.weight, letters[3].weights.last(), 0.0)
    }

    @Test fun secondaryGlyphsPutBanglaDigitsOnTheTopRowAndTakaOnD() {
        fun sec(c: String) = KeyboardLayoutProvider.secondaryFor(Key.Character(c))?.output
        assertEquals("১", sec("q")); assertEquals("০", sec("p"))
        assertEquals("৳", sec("d")); assertEquals("@", sec("a"))
        // The 13-inch number row carries the Latin digits.
        assertEquals("1", KeyboardLayoutProvider.secondaryFor(Key.Symbol("১"))?.output)
        // ? and ! are sentence terminators; the rest are literals.
        assertTrue(KeyboardLayoutProvider.secondaryFor(Key.Symbol("।"))!!.terminator)
        assertFalse(KeyboardLayoutProvider.secondaryFor(Key.Symbol("-"))!!.terminator)
        assertNull(KeyboardLayoutProvider.secondaryFor(Key.Space))
    }

    // ------------------------------------------------------------------ landscape

    @Test fun landscapeKeepsTheRowStructureAndOnlyRetunesTheBottomRowAndHomeRow() {
        for (family in TabletFamily.entries) for (mode in KeyboardMode.entries) {
            val portrait = rows(mode, family, landscape = false)
            val landscape = rows(mode, family, landscape = true)
            assertEquals("$family $mode rows", portrait.size, landscape.size)
            for (i in portrait.indices) assertEquals(portrait[i].keys, landscape[i].keys)
        }
    }

    @Test fun landscapeGivesTheSpaceBarMoreAndTheSideKeysLess() {
        fun command(family: TabletFamily, land: Boolean) = rows(KeyboardMode.LETTERS, family, land).last()
        fun spaceShare(r: KeyboardRow) = r.weights[r.keys.indexOf(Key.Space)] / r.weights.sum()
        // Compact: 5.835 -> 5.770 space, but the side keys fall from 1.046 to 1.021, and wide keys
        // from 1.679 to 1.612, so the space bar's SHARE of the row rises.
        assertTrue(spaceShare(command(TabletFamily.COMPACT, true)) > spaceShare(command(TabletFamily.COMPACT, false)))
        assertTrue(spaceShare(command(TabletFamily.STANDARD, true)) > spaceShare(command(TabletFamily.STANDARD, false)))
        // The 13-inch command row measures identically in both orientations.
        assertEquals(command(TabletFamily.EXTENDED, false), command(TabletFamily.EXTENDED, true))
    }

    @Test fun compactHomeRowIsLessIndentedWithAShorterReturnInLandscape() {
        val portrait = rows(KeyboardMode.LETTERS, TabletFamily.COMPACT, false)[1]
        val landscape = rows(KeyboardMode.LETTERS, TabletFamily.COMPACT, true)[1]
        assertTrue(landscape.leadingFlex < portrait.leadingFlex)
        assertTrue(landscape.weights.last() < portrait.weights.last())
        // The standard family's home row is unaffected by orientation.
        assertEquals(rows(KeyboardMode.LETTERS, TabletFamily.STANDARD, false)[1], rows(KeyboardMode.LETTERS, TabletFamily.STANDARD, true)[1])
    }

    @Test fun onlyTheExtendedNumberRowIsShorter() {
        for (landscape in listOf(false, true)) {
            val extended = rows(KeyboardMode.LETTERS, TabletFamily.EXTENDED, landscape)
            assertEquals(KeyboardLayoutProvider.NUMBER_ROW_HEIGHT, extended.first().heightFactor, 0.0)
            assertTrue(extended.drop(1).all { it.heightFactor == 1.0 })
            for (family in listOf(null, TabletFamily.COMPACT, TabletFamily.STANDARD)) {
                assertTrue(rows(KeyboardMode.LETTERS, family, landscape).all { it.heightFactor == 1.0 })
            }
        }
    }

    @Test fun landscapeGeometryIsItsOwnTable() {
        // iOS measured different margins, gaps and key heights per orientation; they are not scaled.
        assertEquals(6f, TabletFamily.COMPACT.marginDp(false)); assertEquals(7f, TabletFamily.COMPACT.marginDp(true))
        assertEquals(9f, TabletFamily.STANDARD.marginDp(false)); assertEquals(15f, TabletFamily.STANDARD.marginDp(true))
        assertEquals(12f, TabletFamily.COMPACT.gapDp(false)); assertEquals(14f, TabletFamily.COMPACT.gapDp(true))
        assertTrue(TabletFamily.entries.all { it.rowHeightDp(true) >= 50f && it.rowHeightDp(false) >= 50f })
    }
}
