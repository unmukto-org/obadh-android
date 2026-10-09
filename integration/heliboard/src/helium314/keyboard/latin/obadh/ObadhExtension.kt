// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.obadh

import helium314.keyboard.latin.*
import helium314.keyboard.latin.settings.SettingsValues

/** Narrow host contract. The app owns Obadh models and policy; the host owns editor transactions. */
interface ObadhExtension {
    fun suggestions(composer: WordComposer, context: NgramContext, settings: SettingsValues,
                    inputStyle: Int, sequence: Int): SuggestedWords?
    fun decorate(composer: WordComposer, words: SuggestedWords, settings: SettingsValues): SuggestedWords = words
    fun preferredEmoji(emoji: String): String? = null
    fun emojiVariants(emoji: String): List<String> = emptyList()
    fun committed(typed: String, chosen: String, manual: Boolean, settings: SettingsValues)
    fun startInput()
    fun shortcut(trigger: String, settings: SettingsValues): String?
    fun emojiSearch(query: String): List<String>
    fun combiningSpec(spec: String?, settings: SettingsValues): String
    fun pairsEnabled(settings: SettingsValues): Boolean
    fun transformCodePoint(codePoint: Int, settings: SettingsValues): Int
    val clipboardKeys: Boolean
    val returnActions: Boolean
    val emojiSearchBangla: Boolean
}

object ObadhExtensions {
    @Volatile @JvmStatic var current: ObadhExtension? = null
}
