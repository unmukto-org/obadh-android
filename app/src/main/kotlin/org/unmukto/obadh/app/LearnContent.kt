package org.unmukto.obadh.app

import java.text.Normalizer

/** One row of a guide table. [note] explains a trap or a rule, when the row needs it. */
internal data class GuideRow(val roman: String, val bangla: String, val note: String = "")

internal data class GuideSection(val title: String, val intro: String, val rows: List<GuideRow>)

/** One thing to type. [roman] is the answer shown by the hint button. */
internal data class Challenge(val target: String, val roman: String)

internal data class Lesson(val id: String, val title: String, val blurb: String, val challenges: List<Challenge>)

/**
 * Mirrors https://obadh.unmukto.org/guide/. Keep the two in step when the typing scheme changes.
 * LearnContentTest checks that ids are unique and that no lesson is empty.
 */
internal object LearnContent {
    val guide: List<GuideSection> = listOf(
        GuideSection(
            "The idea",
            "Lowercase vowels are short and uppercase are long. Consonants that meet bind into conjuncts unless o separates them. " +
                "Capitals with no meaning fall back to lowercase, so phone auto-capitalization does no harm.",
            listOf(
                GuideRow("kk", "ক্ক", "consonants in a row bind"),
                GuideRow("kok", "কক", "o keeps them apart"),
                GuideRow("kOk", "কোক", "capital O writes the visible ো"),
                GuideRow("nomoskar", "নমস্কার", "o separates n and m"),
            ),
        ),
        GuideSection(
            "Vowels",
            "Standalone vowels, as at the start of a word.",
            listOf(
                GuideRow("o", "অ"), GuideRow("a  A", "আ"), GuideRow("i", "ই"), GuideRow("I", "ঈ"),
                GuideRow("u", "উ"), GuideRow("U", "ঊ"), GuideRow("e  E", "এ"), GuideRow("OI", "ঐ"),
                GuideRow("O", "ও"), GuideRow("OU", "ঔ"), GuideRow("rri", "ঋ"), GuideRow("aY  AY", "অ্যা"),
            ),
        ),
        GuideSection(
            "Vowel signs (kar)",
            "After a consonant the same keys write the sign. Shown on ক.",
            listOf(
                GuideRow("k", "ক", "inherent অ"), GuideRow("ka", "কা"), GuideRow("ki", "কি"), GuideRow("kI", "কী"),
                GuideRow("ku", "কু"), GuideRow("kU", "কূ"), GuideRow("ke", "কে"), GuideRow("kO", "কো"),
                GuideRow("kOI", "কৈ"), GuideRow("kOU", "কৌ"), GuideRow("krri", "কৃ"), GuideRow("kaY", "ক্যা"),
            ),
        ),
        GuideSection(
            "Consonants",
            "Aliases: f = ph, v = bh, S = sh, C = ch, J = j, Y = y.",
            listOf(
                GuideRow("k", "ক"), GuideRow("kh", "খ"), GuideRow("g", "গ"), GuideRow("gh", "ঘ"), GuideRow("Ng", "ঙ"),
                GuideRow("c", "চ"), GuideRow("ch", "ছ"), GuideRow("j", "জ"), GuideRow("jh", "ঝ"), GuideRow("NG", "ঞ"),
                GuideRow("T", "ট", "retroflex"), GuideRow("Th", "ঠ"), GuideRow("D", "ড"), GuideRow("Dh", "ঢ"), GuideRow("N", "ণ"),
                GuideRow("t", "ত", "dental"), GuideRow("th", "থ"), GuideRow("d", "দ"), GuideRow("dh", "ধ"), GuideRow("n", "ন"),
                GuideRow("p", "প"), GuideRow("ph", "ফ"), GuideRow("b", "ব"), GuideRow("bh", "ভ"), GuideRow("m", "ম"),
                GuideRow("z", "য"), GuideRow("r", "র"), GuideRow("l", "ল"), GuideRow("sh", "শ"), GuideRow("Sh", "ষ"),
                GuideRow("s", "স"), GuideRow("h", "হ"), GuideRow("R", "ড়"), GuideRow("Rh", "ঢ়"), GuideRow("y", "য়"),
                GuideRow("w", "ওয়"),
            ),
        ),
        GuideSection(
            "Conjuncts (যুক্তবর্ণ)",
            "Type the consonants in order. The hasant is implied.",
            listOf(
                GuideRow("kt", "ক্ত"), GuideRow("kSh", "ক্ষ"), GuideRow("ndr", "ন্দ্র"), GuideRow("ntro", "ন্ত্র"),
                GuideRow("jNG", "জ্ঞ"), GuideRow("jn", "জ্ঞ"), GuideRow("gg", "জ্ঞ"), GuideRow("nj", "ঞ্জ"),
            ),
        ),
        GuideSection(
            "য-ফলা, ব-ফলা and reph",
            "y after a consonant gives ya-phola, w gives ba-phola. A doubled r before a consonant writes reph.",
            listOf(
                GuideRow("ky", "ক্য"), GuideRow("kw", "ক্ব"), GuideRow("plYan", "প্ল্যান"), GuideRow("blYak", "ব্ল্যাক"),
                GuideRow("rm", "রম", "two letters"), GuideRow("rrm", "র্ম", "reph over ম"), GuideRow("rrk", "র্ক"),
            ),
        ),
        GuideSection(
            "ং ঙ ঁ ঃ ৎ",
            "Nasals and the special signs.",
            listOf(
                GuideRow("bangla", "বাংলা", "ng before a consonant is ং"),
                GuideRow("rong", "রং", "ng at word end is ং"),
                GuideRow("bhanga", "ভাঙা", "ng before a vowel is ঙ"),
                GuideRow("Ng", "ঙ", "always ঙ"),
                GuideRow("M", "ং", "forces a literal anusvar"),
                GuideRow("cA^d", "চাঁদ", "^ after a vowel is ঁ"),
                GuideRow("du:kho", "দুঃখ", ": is ঃ"),
                GuideRow("utsob", "উৎসব", "ts composes ৎ"),
                GuideRow("t``", "ৎ", "two backticks at word end"),
                GuideRow("k,,", "ক্", "dead consonant"),
            ),
        ),
        GuideSection(
            "Numbers, punctuation and loanwords",
            "",
            listOf(
                GuideRow("0–9", "০–৯", "digits convert"), GuideRow("$", "৳", "taka sign"),
                GuideRow("ami.", "আমি।", "full stop becomes dari"),
                GuideRow("q", "ক", "Qatar → কাতার"), GuideRow("x", "ক্স", "box → বক্স"),
                GuideRow("waTar", "ওয়াটার"), GuideRow("aYp", "অ্যাপ"), GuideRow("namaZ", "নামায", "stray Z gives য"),
            ),
        ),
        GuideSection(
            "Common traps",
            "",
            listOf(
                GuideRow("aa", "আআ", "not a long ā; use A or a"),
                GuideRow("boi", "বই", ""), GuideRow("bOI", "বৈ", "capital O makes the sign"),
                GuideRow("bir", "বির", ""), GuideRow("bIr", "বীর", "capital I is long"),
                GuideRow("zoy", "যয়", "z plus oy"),
            ),
        ),
    )

    val lessons: List<Lesson> = listOf(
        Lesson(
            "vowels", "Vowels", "Short keys are short sounds, capitals are long.",
            listOf(
                Challenge("অ", "o"), Challenge("আ", "a"), Challenge("ই", "i"), Challenge("ঈ", "I"),
                Challenge("উ", "u"), Challenge("ঊ", "U"), Challenge("এ", "e"), Challenge("ঐ", "OI"),
                Challenge("ও", "O"), Challenge("ঔ", "OU"), Challenge("অ্যা", "aY"), Challenge("ঋ", "rri"),
            ),
        ),
        Lesson(
            "kar", "Vowel signs", "The same vowel keys, after a consonant.",
            listOf(
                Challenge("কা", "ka"), Challenge("কি", "ki"), Challenge("কী", "kI"), Challenge("কু", "ku"),
                Challenge("কূ", "kU"), Challenge("কে", "ke"), Challenge("কো", "kO"), Challenge("কৈ", "kOI"),
                Challenge("কৌ", "kOU"), Challenge("কৃ", "krri"), Challenge("ক্যা", "kaY"),
            ),
        ),
        Lesson(
            "consonants", "Consonants", "Watch the capitals: T and t are different letters.",
            listOf(
                Challenge("খ", "kh"), Challenge("ঘ", "gh"), Challenge("ঙ", "Ng"), Challenge("ছ", "ch"),
                Challenge("ঝ", "jh"), Challenge("ঞ", "NG"), Challenge("ট", "T"), Challenge("ঠ", "Th"),
                Challenge("ড", "D"), Challenge("ঢ", "Dh"), Challenge("ণ", "N"), Challenge("ত", "t"),
                Challenge("থ", "th"), Challenge("ধ", "dh"), Challenge("ফ", "ph"), Challenge("ভ", "bh"),
                Challenge("শ", "sh"), Challenge("ষ", "Sh"), Challenge("ড়", "R"), Challenge("ঢ়", "Rh"),
            ),
        ),
        Lesson(
            "words", "First words", "Real words. Keep the o when two consonants must stay apart.",
            listOf(
                Challenge("আমি", "ami"), Challenge("বই", "boi"), Challenge("খেলা", "khela"),
                Challenge("আমরা", "amora"), Challenge("বীর", "bIr"), Challenge("কোক", "kOk"),
                Challenge("কক", "kok"), Challenge("নমস্কার", "nomoskar"), Challenge("ভাষা", "bhaSha"),
            ),
        ),
        Lesson(
            "conjuncts", "Conjuncts", "Consonants in a row bind together.",
            listOf(
                Challenge("ক্ত", "kt"), Challenge("ক্ষ", "kSh"), Challenge("ক্ক", "kk"), Challenge("ন্দ্র", "ndr"),
                Challenge("জ্ঞ", "jNG"), Challenge("ক্য", "ky"), Challenge("ক্ব", "kw"), Challenge("প্ল্যান", "plYan"),
                Challenge("ব্ল্যাক", "blYak"), Challenge("র্ম", "rrm"), Challenge("বিজ্ঞান", "Biggan"),
            ),
        ),
        Lesson(
            "signs", "Special signs", "ং ঙ ঁ ঃ ৎ, each with its own trick.",
            listOf(
                Challenge("বাংলা", "bangla"), Challenge("রং", "rong"), Challenge("ভাঙা", "bhanga"),
                Challenge("রঙিন", "rongin"), Challenge("চাঁদ", "cA^d"), Challenge("দুঃখ", "du:kho"),
                Challenge("উৎসব", "utsob"), Challenge("সংগ", "sMgo"),
            ),
        ),
        Lesson(
            "loanwords", "Loanwords and sentences", "q, x, w, Z and a whole sentence.",
            listOf(
                Challenge("কাতার", "qatar"), Challenge("বক্স", "box"), Challenge("ওয়াটার", "waTar"),
                Challenge("অ্যাপ", "aYp"), Challenge("নামায", "namaZ"), Challenge("ভাষা হোক আরও উন্মুক্ত", "bhaSha hOk aroO unmukt"),
            ),
        ),
    )

    fun lesson(id: String) = lessons.firstOrNull { it.id == id }
}

/** NFC-normalised, trimmed. Android text fields may hand back the same word composed differently. */
internal fun normalizeAnswer(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).trim()

internal fun isCorrect(typed: String, challenge: Challenge) = normalizeAnswer(typed) == normalizeAnswer(challenge.target)

/** Three stars for a clean run, two for up to two hints, otherwise one. */
internal fun starsFor(hintsUsed: Int, skipped: Int) = when {
    hintsUsed == 0 && skipped == 0 -> 3
    hintsUsed + skipped <= 2 -> 2
    else -> 1
}
