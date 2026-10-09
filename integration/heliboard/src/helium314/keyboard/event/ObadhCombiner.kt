// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import org.unmukto.obadh.engine.ObadhBridgeClient
import org.unmukto.obadh.engine.RomanInputRules
import java.util.ArrayList

/** Obadh owns Bangla conversion; HeliBoard owns the editor and Latin input pipeline. */
class ObadhCombiner(
    private val convert: (String) -> String = NativeTransliterator::convert,
) : Combiner {
    private val roman = StringBuilder()
    private var rendered = ""
    var romanInput = ""
        private set

    override val combiningStateFeedback: CharSequence
        get() = rendered

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.keyCode == KeyCode.SHIFT) return event
        if (event.keyCode == KeyCode.DELETE) {
            if (roman.isEmpty()) return event
            // Obadh's explicit qq escape is one logical editing unit, matching the existing IME.
            if (RomanInputRules.deleteCount(roman) == 2) roman.setLength(roman.length - 2)
            else roman.delete(roman.length - Character.charCount(roman.codePointBefore(roman.length)), roman.length)
            romanInput = roman.toString()
            rendered = if (roman.isEmpty()) "" else convert(RomanInputRules.engineInput(roman.toString()))
            if (roman.isEmpty()) {
                // Upstream's Hangul/Khipro convention clears the editor's final composing character.
                return Event.createHardwareKeypressEvent(0x20, Constants.CODE_SPACE, 0, event, event.isKeyRepeat)
            }
            return Event.createConsumedEvent(event)
        }
        val cp = event.codePoint
        if (!event.isFunctionalKeyEvent && (cp in 'a'.code..'z'.code || cp in 'A'.code..'Z'.code)) {
            roman.appendCodePoint(cp)
            romanInput = roman.toString()
            rendered = convert(RomanInputRules.engineInput(roman.toString()))
            return Event.createConsumedEvent(event)
        }
        // Do not feed punctuation, digits, editor actions or mode switches into transliteration.
        if (roman.isEmpty()) return event
        val converted = combiningStateFeedback.toString()
        // Keep the immutable Roman snapshot until WordComposer commits/resets the word.
        roman.setLength(0); rendered = ""
        return Event.createSoftwareTextEvent(converted, KeyCode.MULTIPLE_CODE_POINTS, event)
    }

    override fun reset() { roman.setLength(0); rendered = ""; romanInput = "" }

    companion object {
        /** Called at IME startup, before the first input view is shown. */
        @JvmStatic fun warmUp() { NativeTransliterator.convert("a") }
    }

    /** One tiny process-lifetime transliteration handle; no dictionary/model copying on this path. */
    private object NativeTransliterator {
        private val engine by lazy { ObadhBridgeClient().apply { initializeTransliteration() } }
        fun convert(roman: String): String =
            engine.transliterate(roman)
    }
}
