package com.example.kokoro82m.tts.g2p.misaki


// --- Data Classes ---
data class MToken(
    var text: String,
    var tag: String, // spaCy POS tag from your equivalent library
    var whitespace: String,
    var phonemes: String? = null,
    var startTs: Float? = null,
    var endTs: Float? = null,
    var underscore: Underscore = Underscore() // Mutable for easier updates
) {
    data class Underscore(
        var isHead: Boolean = true,
        var alias: String? = null,
        var stress: Float? = null, // Can be Int or Float in Python, use Float for flexibility
        var currency: String? = null, // e.g., "$", "£"
        var numFlags: String = "", // e.g., "a&n"
        var prespace: Boolean = false,
        var rating: Int? = null // Phoneme quality rating (1-5)
    )
}

data class TokenContext(
    val futureVowel: Boolean? = null,
    val futureTo: Boolean = false
)

// --- Constants ---
object MisakiG2PConstants {
    val DIPHTHONGS = setOf('A', 'I', 'O', 'Q', 'W', 'Y', 'ʤ', 'ʧ')
    const val STRESSES = "ˌˈ"
    const val PRIMARY_STRESS = STRESSES[1]
    const val SECONDARY_STRESS = STRESSES[0]
    val VOWELS = setOf('A', 'I', 'O', 'Q', 'W', 'Y', 'a', 'i', 'u', 'æ', 'ɑ', 'ɒ', 'ɔ', 'ə', 'ɛ', 'ɜ', 'ɪ', 'ʊ', 'ʌ', 'ᵻ')
    val CONSONANTS = setOf('b', 'd', 'f', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 's', 't', 'v', 'w', 'z', 'ð', 'ŋ', 'ɡ', 'ɹ', 'ɾ', 'ʃ', 'ʒ', 'ʤ', 'ʧ', 'θ')
    val US_TAUS = setOf('A', 'I', 'O', 'W', 'Y', 'i', 'u', 'æ', 'ɑ', 'ə', 'ɛ', 'ɪ', 'ɹ', 'ʊ', 'ʌ')

    val CURRENCIES = mapOf(
        '$' to Pair("dollar", "cent"),
        '£' to Pair("pound", "pence"),
        '€' to Pair("euro", "cent")
    )
    val ORDINALS = setOf("st", "nd", "rd", "th")
    val ADD_SYMBOLS = mapOf("." to "dot", "/" to "slash")
    val SYMBOLS = mapOf("%" to "percent", "&" to "and", "+" to "plus", "@" to "at")

    val US_VOCAB = setOf('A', 'I', 'O', 'W', 'Y', 'b', 'd', 'f', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'p', 's', 't', 'u', 'v', 'w', 'z', 'æ', 'ð', 'ŋ', 'ɑ', 'ɔ', 'ə', 'ɛ', 'ɜ', 'ɡ', 'ɪ', 'ɹ', 'ɾ', 'ʃ', 'ʊ', 'ʌ', 'ʒ', 'ʤ', 'ʧ', 'ˈ', 'ˌ', 'θ', 'ᵊ', 'ᵻ', 'ʔ')
    val GB_VOCAB = setOf('A', 'I', 'Q', 'W', 'Y', 'a', 'b', 'd', 'f', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'p', 's', 't', 'u', 'v', 'w', 'z', 'ð', 'ŋ', 'ɑ', 'ɒ', 'ɔ', 'ə', 'ɛ', 'ɜ', 'ɡ', 'ɪ', 'ɹ', 'ʃ', 'ʊ', 'ʌ', 'ʒ', 'ʤ', 'ʧ', 'ˈ', 'ˌ', 'ː', 'θ', 'ᵊ')

    val LEXICON_ORDS = listOf(39, 45) + (65..90) + (97..122) // ASCII ',-,A-Z,a-z

    // Python `regex` module pattern. Kotlin's Regex supports \p{L}, \p{Lu}, \p{Ll}.
    val SUBTOKEN_REGEX_PATTERN = "^['‘’]+|\\p{Lu}(?=\\p{Lu}\\p{Ll})|(?:^-)?(?:\\d?[,.]?\\d)+|[-_]+|['‘’]{2,}|\\p{L}*?(?:['‘’]\\p{L})*?\\p{Ll}(?=\\p{Lu})|\\p{L}+(?:['‘’]\\p{L})*|[^-_\\p{L}'‘’\\d]|['‘’]+$"
    val SUBTOKEN_REGEX = Regex(SUBTOKEN_REGEX_PATTERN)

    val LINK_REGEX = Regex("""\[([^]]+)]\(([^)]*)\)""")
    val SUBTOKEN_JUNKS = setOf('\'', ',', '-', '.', '_', '‘', '’', '/')
    val PUNCTS = setOf(';', ':', ',', '.', '!', '?', '—', '…', '"', '“', '”') // Note: Python had "“”" as single element
    val NON_QUOTE_PUNCTS = PUNCTS.filterNot { it in setOf('"', '“', '”') }.toSet()

    val PUNCT_TAGS = setOf(".", ",", "-LRB-", "-RRB-", "``", "\"\"", "''", ":", "$", "#", "NFP")
    val PUNCT_TAG_PHONEMES = mapOf(
        "-LRB-" to "(", "-RRB-" to ")", "``" to "“", "\"\"" to "”", "''" to "”"
    )
}