package org.unmukto.obadh.engine

import java.text.BreakIterator

/** The slice of InputConnection the composer needs. Fakeable in tests. */
interface TextDocumentEditing {
    val contextBeforeInput: String?
    fun insertText(text: String)
    /** Host-defined unit, like iOS deleteBackward. Only for the user's backspace key. */
    fun deleteBackward()
    /**
     * Delete exactly [charCount] UTF-16 units before the cursor, in order with the
     * inserts. Rewrites must use this, never a delete-and-reread loop: hosts may apply
     * key events after our next read, which would over-delete (eating the space
     * before the word).
     */
    fun deleteBeforeCursor(charCount: Int)
}

/**
 * Renders the word being composed as ORDINARY document text and rewrites it in
 * place, instead of using Android composing text (setComposingText).
 *
 * Composing spans bind the insertion point to the region until finished and
 * their cursor/selection callbacks vary by host editor. Real text sidesteps
 * that: the cursor moves freely, mid-text edits are plain edits, and switching
 * keyboards mid-word keeps the word.
 *
 * Only state is [composedText], the exact string last inserted for the word.
 * Every rewrite verifies it is still at the cursor first, so an unobserved
 * cursor move or host edit can never make us delete text we do not own.
 */
class TextCompositionController {
    var composedText = ""
        private set

    val hasActiveComposition: Boolean get() = composedText.isNotEmpty()

    /** Forget the word without touching the document (cursor moved, keyboard switched, host rewrote). */
    fun resetHostState() {
        composedText = ""
    }

    fun setComposition(text: String, document: TextDocumentEditing) {
        val current = composedText
        val context = document.contextBeforeInput ?: ""

        if (current.isNotEmpty() && !context.endsWith(current)) {
            // Not where we left it: do not delete whatever is at the cursor now.
            composedText = ""
            if (text.isNotEmpty()) document.insertText(text)
            composedText = text
            return
        }

        // Fast path: new rendering only appends code points (a vowel sign joining
        // the cluster). Insert just the tail; no delete, no flicker. A grapheme
        // prefix would miss this: "বা" is not a grapheme prefix of "বাং".
        if (text.startsWith(current)) {
            val tail = text.substring(current.length)
            if (tail.isNotEmpty()) document.insertText(tail)
            composedText = text
            return
        }

        // General case: we verified `current` is the text right before the cursor, so
        // the changed suffix is exactly known. Delete precisely that, then insert.
        val keep = graphemeAlignedCommonPrefix(current, text)
        document.deleteBeforeCursor(current.length - keep.length)
        val insertion = text.substring(keep.length)
        if (insertion.isNotEmpty()) document.insertText(insertion)
        composedText = text
    }

    /** Finalize the word (optionally replacing it with [finalText]) then append [trailingText]. */
    fun commit(finalText: String, trailingText: String = "", document: TextDocumentEditing): Boolean {
        if (finalText.isNotEmpty() && finalText != composedText) setComposition(finalText, document)
        if (trailingText.isNotEmpty()) document.insertText(trailingText)
        val didCommit = hasActiveComposition || trailingText.isNotEmpty()
        composedText = ""
        return didCommit
    }

    /** Replace the current word with an accepted suggestion; caller decides what follows. */
    fun commitSuggestion(text: String, document: TextDocumentEditing) {
        if (text.isEmpty()) return
        setComposition(text, document)
        composedText = ""
    }

    fun commitNextWordSuggestion(text: String, document: TextDocumentEditing) {
        if (text.isEmpty()) return
        val previous = document.contextBeforeInput?.lastOrNull()
        if (previous != null && !previous.isWhitespace()) document.insertText(" ")
        document.insertText(text)
        document.insertText(" ")
    }

    /** The space key always inserts; the double-space dari shortcut belongs to the caller. */
    fun insertSpace(document: TextDocumentEditing) = document.insertText(" ")

    fun clearComposition(document: TextDocumentEditing) = setComposition("", document)

    /** Replace [word], which must be the text right before the cursor. */
    fun replaceWordBeforeCursor(word: String, replacement: String, document: TextDocumentEditing) {
        if (word.isEmpty()) return
        val context = document.contextBeforeInput ?: ""
        if (!context.endsWith(word)) return
        document.deleteBeforeCursor(word.length)
        if (replacement.isNotEmpty()) document.insertText(replacement)
    }

    companion object {
        /** Longest shared prefix that ends on a grapheme boundary in both strings. */
        fun graphemeAlignedCommonPrefix(a: String, b: String): String {
            var n = 0
            while (n < a.length && n < b.length && a[n] == b[n]) n++
            if (n == a.length && n == b.length) return a
            while (n > 0 && !(isBoundary(a, n) && isBoundary(b, n))) n--
            return a.substring(0, n)
        }

        private fun isBoundary(s: String, index: Int): Boolean {
            if (index == 0 || index == s.length) return true
            val it = BreakIterator.getCharacterInstance()
            it.setText(s)
            return it.isBoundary(index)
        }
    }
}
