package org.unmukto.obadh.engine

/** Delete [deleteBefore] chars before the caret, then insert [insertion]. */
data class SmartPunctuationResult(val deleteBefore: Int, val insertion: String) {
    companion object {
        fun insert(text: String) = SmartPunctuationResult(0, text)
    }
}

/** Apple-style smart punctuation, on the client layer; the engine only maps `.`->`।` and `$`->`৳`. */
object SmartPunctuation {
    const val EM_DASH = "—"
    const val ELLIPSIS = "…"
    const val DARI = "।"
    private const val LEFT_DQ = "“"
    private const val RIGHT_DQ = "”"
    private const val LEFT_SQ = "‘"
    private const val RIGHT_SQ = "’"

    fun literalSubstitution(raw: String, contextBefore: String): SmartPunctuationResult = when {
        raw == "-" && contextBefore.endsWith("-") && !contextBefore.endsWith(EM_DASH) ->
            SmartPunctuationResult(1, EM_DASH)
        raw == "." && endsWithTwoLiteralDots(contextBefore) -> SmartPunctuationResult(2, ELLIPSIS)
        raw == "\"" -> SmartPunctuationResult.insert(if (isOpeningContext(contextBefore)) LEFT_DQ else RIGHT_DQ)
        raw == "'" -> SmartPunctuationResult.insert(if (isOpeningContext(contextBefore)) LEFT_SQ else RIGHT_SQ)
        else -> SmartPunctuationResult.insert(raw)
    }

    /** After a word, a second quick space becomes `। `. Null when it should not fire. */
    fun doubleSpaceSubstitution(contextBefore: String): SmartPunctuationResult? {
        if (!contextBefore.endsWith(" ")) return null
        val last = contextBefore.dropLast(1).lastOrNull() ?: return null
        if (!(last.isLetter() || last.isDigit() || Character.getType(last) == Character.NON_SPACING_MARK.toInt() ||
                Character.getType(last) == Character.COMBINING_SPACING_MARK.toInt())
        ) return null
        return SmartPunctuationResult(1, "$DARI ")
    }

    private fun endsWithTwoLiteralDots(context: String) = context.takeLastWhile { it == '.' }.length >= 2

    private fun isOpeningContext(contextBefore: String): Boolean {
        val last = contextBefore.lastOrNull() ?: return true
        return last.isWhitespace() || "([{“‘।".indexOf(last) >= 0
    }
}
