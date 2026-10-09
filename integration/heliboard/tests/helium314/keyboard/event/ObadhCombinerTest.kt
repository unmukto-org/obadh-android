// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import org.junit.Assert.*
import org.junit.Test

class ObadhCombinerTest {
    private fun key(code: Int) = Event.createSoftwareKeypressEvent(code, 0, 0, 0, false)
    private fun type(combiner: ObadhCombiner, text: String) = text.forEach {
        assertTrue(combiner.processEvent(arrayListOf(), key(it.code)).isConsumed)
    }

    @Test fun boundaryCommitsBanglaAndPreservesOriginalEvent() {
        val combiner = ObadhCombiner { if (it == "bangla") "বাংলা" else it }
        type(combiner, "bangla")
        assertEquals("বাংলা", combiner.combiningStateFeedback)
        val punctuation = key('.'.code)
        val result = combiner.processEvent(arrayListOf(), punctuation)
        assertEquals("বাংলা", result.textToCommit)
        assertSame(punctuation, result.nextEvent)
        assertEquals("", combiner.combiningStateFeedback)
        assertEquals("bangla", combiner.romanInput)
        combiner.reset()
        assertEquals("", combiner.romanInput)
    }

    @Test fun escapeDeletionIsAtomicAndDoesNotDeleteCommittedText() {
        val combiner = ObadhCombiner { it }
        type(combiner, "aqq")
        assertTrue(combiner.processEvent(arrayListOf(), key(KeyCode.DELETE)).isConsumed)
        assertEquals("a", combiner.combiningStateFeedback)
        val delete = key(KeyCode.DELETE)
        val clearLast = combiner.processEvent(arrayListOf(), delete)
        assertEquals(' '.code, clearLast.codePoint)
        assertSame(delete, clearLast.nextEvent)
        assertSame(delete, combiner.processEvent(arrayListOf(), delete))
    }

    @Test fun resetAndShiftDoNotLeakCompositionAcrossLanguages() {
        val combiner = ObadhCombiner { it }
        type(combiner, "ami")
        val shift = key(KeyCode.SHIFT)
        assertSame(shift, combiner.processEvent(arrayListOf(), shift))
        assertEquals("ami", combiner.combiningStateFeedback)
        combiner.reset()
        val digit = key('1'.code)
        assertSame(digit, combiner.processEvent(arrayListOf(), digit))
        assertEquals("", combiner.combiningStateFeedback)
    }

    @Test fun feedbackReadsDoNotRepeatNativeConversion() {
        var calls = 0
        val combiner = ObadhCombiner { calls++; it }
        type(combiner, "ami")
        repeat(20) { assertEquals("ami", combiner.combiningStateFeedback) }
        assertEquals(3, calls)
        combiner.processEvent(arrayListOf(), key(' '.code))
        assertEquals(3, calls)
    }

    @Test fun newWordAndDeletionDoNotReuseBoundaryRomanSnapshot() {
        val combiner = ObadhCombiner { it }
        type(combiner, "hello")
        combiner.processEvent(arrayListOf(), key(' '.code))
        type(combiner, "a")
        assertEquals("a", combiner.romanInput)
        combiner.processEvent(arrayListOf(), key(KeyCode.DELETE))
        assertEquals("", combiner.romanInput)
    }

    @Test fun oddTrailingQAndKhandaTaMatchObadhInputRules() {
        val combiner = ObadhCombiner { it }
        type(combiner, "tq")
        assertEquals("t``", combiner.combiningStateFeedback)
        type(combiner, "q")
        assertEquals("tqq", combiner.combiningStateFeedback)
        type(combiner, "q")
        combiner.processEvent(arrayListOf(), key(KeyCode.DELETE))
        assertEquals("tqq", combiner.combiningStateFeedback)
        combiner.processEvent(arrayListOf(), key(KeyCode.DELETE))
        assertEquals("t", combiner.combiningStateFeedback)
    }
}
