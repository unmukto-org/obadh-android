package org.unmukto.obadh.engine

/**
 * Client-owned auto-insert policy over the engine's provenance. Same hurdles and
 * constants as iOS (see obadh-ios docs/autocorrect.md); they are calibrated
 * against the real artifacts, so move them only together with the tests.
 */
object AutoInsertGate {
    val confidentSources = setOf(
        DetailedCorrection.Source.EDIT_DISTANCE,
        DetailedCorrection.Source.DIACRITIC_EDIT,
        DetailedCorrection.Source.ORTHOGRAPHIC_VOWEL_LENGTH,
        DetailedCorrection.Source.CONSONANT_CONFUSION,
        DetailedCorrection.Source.ROMAN_REPAIR_EXACT,
        DetailedCorrection.Source.ENGLISH_LOANWORD_EXACT,
    )

    const val CORRECTION_FREQUENCY_FLOOR = 40L
    const val RARE_BASELINE_RATIO = 50.0

    fun maxEditCost(source: Int): Int =
        if (source == DetailedCorrection.Source.CONSONANT_CONFUSION) 3 else 1

    fun shouldAutoInsert(
        baselineFrequency: Long,
        correction: DetailedCorrection,
        isProtected: Boolean,
    ): Boolean {
        if (isProtected) return false
        if (correction.source !in confidentSources) return false
        if (correction.editCost > maxEditCost(correction.source)) return false
        if ((correction.romanRepairCost ?: 0) > 1) return false
        if (correction.frequency < CORRECTION_FREQUENCY_FLOOR) return false
        if (baselineFrequency == 0L) return true
        return correction.frequency.toDouble() >= RARE_BASELINE_RATIO * baselineFrequency.toDouble()
    }
}
