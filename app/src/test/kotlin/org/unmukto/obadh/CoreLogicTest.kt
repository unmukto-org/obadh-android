package org.unmukto.obadh

import org.junit.Assert.*
import org.junit.Test
import org.unmukto.obadh.engine.*

private class FakeEngine : BanglaTypingEngine {
    override fun transliterate(input: String) = "<$input>"
    override fun compositionSuggestions(romanInput: String, limit: Int) = emptyList<String>()
    override fun autosuggestSuggestions(context: String, limit: Int) = emptyList<String>()
}

private class FakeDocument(var text: String = "") : TextDocumentEditing {
    override val contextBeforeInput get() = text
    override fun insertText(text: String) { this.text += text }
    override fun deleteBackward() { text = text.dropLast(1) }
    override fun deleteBeforeCursor(charCount: Int) { text = text.dropLast(charCount) }
}

/** A host whose key-event deletes land late: deleteBackward is a no-op until later. */
private class LaggyKeyEventDocument(var text: String) : TextDocumentEditing {
    override val contextBeforeInput get() = text
    override fun insertText(text: String) { this.text += text }
    override fun deleteBackward() { /* applied after our next read */ }
    override fun deleteBeforeCursor(charCount: Int) { text = text.dropLast(charCount) }
}

class CoreLogicTest {
    @Test fun khandaTaShortcutOnlyForSingleQ() {
        assertEquals("sot``", KeyboardComposer.engineInput("sotq"))
        assertEquals("tqq", KeyboardComposer.engineInput("tqq"))
        assertEquals("Ut``", KeyboardComposer.engineInput("Utq"))
    }

    @Test fun backspaceRemovesQQAsOneUnit() {
        val c = KeyboardComposer(FakeEngine())
        "baqq".forEach { c.append(it.toString()) }
        c.deleteBackward()
        assertEquals("ba", c.romanBuffer)
        val d = KeyboardComposer(FakeEngine())
        "qqq".forEach { d.append(it.toString()) }
        d.deleteBackward()
        assertEquals("qq", d.romanBuffer)
    }

    @Test fun staleAutocorrectResultIsDropped() {
        val c = KeyboardComposer(FakeEngine())
        c.append("a")
        val stale = c.generation
        c.append("b")
        c.mergeAutocorrectCandidates(listOf("x"), stale)
        assertEquals(1, c.activeSuggestions.size)
    }

    @Test fun gateRejectsUnknownAndCompletionChannels() {
        fun cand(src: Int, edit: Int = 1, freq: Long = 1000) = DetailedCorrection("w", src, edit, null, freq)
        assertFalse(AutoInsertGate.shouldAutoInsert(0, cand(99), false))
        assertFalse(AutoInsertGate.shouldAutoInsert(0, cand(DetailedCorrection.Source.PREFIX_COMPLETION), false))
        assertTrue(AutoInsertGate.shouldAutoInsert(0, cand(DetailedCorrection.Source.EDIT_DISTANCE), false))
        assertFalse(AutoInsertGate.shouldAutoInsert(0, cand(DetailedCorrection.Source.EDIT_DISTANCE), true))
        assertFalse(AutoInsertGate.shouldAutoInsert(0, cand(DetailedCorrection.Source.EDIT_DISTANCE, freq = 39), false))
    }

    @Test fun gateRatioRuleMatchesManusCase() {
        val c = DetailedCorrection("মানুষ", DetailedCorrection.Source.CONSONANT_CONFUSION, 3, null, 95_278)
        assertTrue(AutoInsertGate.shouldAutoInsert(49, c, false))
        assertFalse(AutoInsertGate.shouldAutoInsert(5_000, c, false))
    }

    @Test fun appendOnlyRenderingInsertsTailWithoutDeleting() {
        val doc = FakeDocument()
        val ctl = TextCompositionController()
        ctl.setComposition("বা", doc)
        ctl.setComposition("বাং", doc)
        assertEquals("বাং", doc.text)
        assertEquals("বাং", ctl.composedText)
    }

    @Test fun reshapeReplacesOnlyChangedSuffix() {
        val doc = FakeDocument("hello ")
        val ctl = TextCompositionController()
        ctl.setComposition("কি", doc)
        ctl.setComposition("কো", doc)
        assertEquals("hello কো", doc.text)
    }

    @Test fun rewriteNeverEatsThePrecedingSpace() {
        // "jukto borrno": a reshape of the second word must leave "যুক্ত " intact,
        // even on a host that applies key-event deletes late.
        val doc = LaggyKeyEventDocument("যুক্ত ")
        val ctl = TextCompositionController()
        listOf("ব", "বর", "বর্", "বর্ন", "বর্ণ").forEach { ctl.setComposition(it, doc) }
        ctl.setComposition("বর্ণ", doc)
        assertEquals("যুক্ত বর্ণ", doc.text)
        ctl.setComposition("বরণ", doc)
        assertEquals("যুক্ত বরণ", doc.text)
    }

    @Test fun movedCursorNeverDeletesForeignText() {
        val doc = FakeDocument()
        val ctl = TextCompositionController()
        ctl.setComposition("আমি", doc)
        doc.text = "other" // host changed text under us
        ctl.setComposition("আমার", doc)
        assertEquals("otherআমার", doc.text)
    }

    @Test fun packedStringListRoundTrips() {
        val a = "আমি".toByteArray(); val b = byteArrayOf()
        fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
        val bytes = le(2) + le(a.size) + a + le(b.size) + b
        assertEquals(listOf("আমি", ""), PackedRecords.parseStringList(bytes))
    }

    @Test fun smartPunctuation() {
        assertEquals(SmartPunctuationResult(1, "—"), SmartPunctuation.literalSubstitution("-", "a-"))
        assertEquals(SmartPunctuationResult(2, "…"), SmartPunctuation.literalSubstitution(".", "a.."))
        assertEquals("“", SmartPunctuation.literalSubstitution("\"", "").insertion)
        assertEquals(SmartPunctuationResult(1, "। "), SmartPunctuation.doubleSpaceSubstitution("আমি "))
        assertNull(SmartPunctuation.doubleSpaceSubstitution("আমি"))
    }
}
