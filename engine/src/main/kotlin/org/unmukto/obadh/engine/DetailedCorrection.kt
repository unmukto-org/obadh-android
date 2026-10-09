package org.unmukto.obadh.engine

/**
 * One ranked correction with the provenance the auto-insert gate is built on.
 * Mirrors the engine's packed record exactly.
 */
data class DetailedCorrection(
    val text: String,
    val source: Int,
    val editCost: Int,
    /** null when the engine reports no roman-side repair (wire value 0xFFFF). */
    val romanRepairCost: Int?,
    val frequency: Long,
) {
    /** Frozen, append-only source codes (`FstCandidateSource`). Unknown = never auto-replace. */
    object Source {
        const val EXACT = 0
        const val EDIT_DISTANCE = 1
        const val DIACRITIC_EDIT = 2
        const val ORTHOGRAPHIC_VOWEL_LENGTH = 3
        const val PREFIX_COMPLETION = 4
        const val STEM_SUFFIX_COMPLETION = 5
        const val SKELETON_VOWEL_DROP = 6
        const val CONSONANT_CONFUSION = 7
        const val ROMAN_REPAIR_EXACT = 8
        const val ENGLISH_LOANWORD_EXACT = 9
        const val ENGLISH_LOANWORD_FUZZY = 10
    }
}
