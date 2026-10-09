package org.unmukto.obadh.engine

/** Shared Android input conventions; actual transliteration remains in the engine C ABI. */
object RomanInputRules {
    fun deleteCount(input: CharSequence): Int {
        var qs = 0
        for (i in input.length - 1 downTo 0) {
            if (input[i] != 'q') break
            qs++
        }
        return if (qs > 0 && qs % 2 == 0) 2 else 1
    }

    fun engineInput(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if ((c == 't' || c == 'T') && i + 1 < input.length && input[i + 1] == 'q' &&
                (i + 2 == input.length || input[i + 2] != 'q')) {
                out.append(c).append("``")
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
