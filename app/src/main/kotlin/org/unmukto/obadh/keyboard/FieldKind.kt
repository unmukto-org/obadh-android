package org.unmukto.obadh.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** The kind of field being typed into, from its input type. Shapes the keys and the language. */
enum class FieldKind(val forcesEnglish: Boolean = false) {
    TEXT,
    EMAIL(true),
    URL(true),
    PASSWORD(true),
    NAME,
    NUMBER,
    PHONE;

    companion object {
        fun of(info: EditorInfo?): FieldKind = info?.let { ofInputType(it.inputType) } ?: TEXT

        fun ofInputType(type: Int): FieldKind {
            val variation = type and InputType.TYPE_MASK_VARIATION
            return when (type and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER ->
                    if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) PASSWORD else NUMBER
                InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> PHONE
                InputType.TYPE_CLASS_TEXT -> when (variation) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EMAIL
                    InputType.TYPE_TEXT_VARIATION_URI -> URL
                    InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                    InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> NAME
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> PASSWORD
                    else -> TEXT
                }
                else -> TEXT
            }
        }
    }
}
