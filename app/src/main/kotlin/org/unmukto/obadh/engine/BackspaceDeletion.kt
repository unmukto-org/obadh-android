package org.unmukto.obadh.engine

enum class BackspaceDeletionUnit { CHARACTER, WORD, SENTENCE }

object BackspaceDeletionPlanner {
    /** Number of code points to delete before the cursor for [unit]. */
    fun deleteCount(context: String, unit: BackspaceDeletionUnit): Int {
        if (context.isEmpty()) return 0
        val cps = context.codePoints().toArray()
        var i = cps.size
        when (unit) {
            BackspaceDeletionUnit.CHARACTER -> return 1
            BackspaceDeletionUnit.WORD -> {
                while (i > 0 && isBoundary(cps[i - 1])) i--
                while (i > 0 && !isBoundary(cps[i - 1])) i--
            }
            BackspaceDeletionUnit.SENTENCE -> {
                while (i > 0 && Character.isWhitespace(cps[i - 1])) i--
                while (i > 0 && !isSentenceBoundary(cps[i - 1])) i--
            }
        }
        return maxOf(cps.size - i, 1)
    }

    private fun isBoundary(cp: Int): Boolean {
        if (Character.isWhitespace(cp) || cp == 0x0964) return true
        return when (Character.getType(cp).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
            Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    private fun isSentenceBoundary(cp: Int) =
        cp == 0x0964 || cp == '.'.code || cp == '!'.code || cp == '?'.code || cp == '\n'.code
}

/** Native-like hold curve: immediate delete, fast character repeat, then word chunks. */
object BackspaceRepeatPolicy {
    data class Stage(val unit: BackspaceDeletionUnit, val intervalMs: Long)

    private const val INITIAL_DELAY_MS = 380L
    private const val MEDIUM_MS = 1200L
    private const val FAST_MS = 2800L
    private const val FASTEST_MS = 4200L

    fun stage(elapsedMs: Long): Stage? = when {
        elapsedMs < INITIAL_DELAY_MS -> null
        elapsedMs < MEDIUM_MS -> Stage(BackspaceDeletionUnit.CHARACTER, 55)
        elapsedMs < FAST_MS -> Stage(BackspaceDeletionUnit.CHARACTER, 44)
        elapsedMs < FASTEST_MS -> Stage(BackspaceDeletionUnit.WORD, 120)
        else -> Stage(BackspaceDeletionUnit.WORD, 90)
    }
}
