package org.unmukto.obadh

import org.junit.Assert.*
import org.junit.Test
import org.unmukto.obadh.keyboard.*

class KeyboardLayoutTest {
    private fun rows(mode: KeyboardMode, family: TabletFamily?) = KeyboardLayoutProvider.rows(mode, true, family)

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

    @Test fun everyTabletCommandRowHasOneSpaceEmojiAndHideKey() {
        for (family in TabletFamily.entries) for (mode in KeyboardMode.entries) {
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
        assertEquals(listOf(Key.ModeSwitch("123", KeyboardMode.NUMBERS), Key.Emoji, Key.Globe, Key.Space, Key.Return), letters[3].keys)
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
}
