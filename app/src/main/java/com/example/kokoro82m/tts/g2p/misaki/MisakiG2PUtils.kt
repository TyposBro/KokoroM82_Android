package com.example.kokoro82m.tts.g2p.misaki


// --- Utility Functions ---
fun mergeTokens(tokens: List<MToken>, unk: String? = null): MToken {
    if (tokens.isEmpty()) throw IllegalArgumentException("Cannot merge empty token list")

    val stresses = tokens.mapNotNull { it.underscore.stress }.distinct()
    val currencies = tokens.mapNotNull { it.underscore.currency }.distinct()
    val ratings = tokens.map { it.underscore.rating }

    val phonemesStr = if (unk == null) {
        null
    } else {
        buildString {
            tokens.forEach { tk ->
                if (tk.underscore.prespace && isNotEmpty() && last() != ' ' && !tk.phonemes.isNullOrEmpty()) {
                    append(' ')
                }
                append(tk.phonemes ?: unk)
            }
        }
    }

    val text = tokens.dropLast(1).joinToString("") { it.text + it.whitespace } + tokens.last().text
    val tag = tokens.maxByOrNull { tk -> tk.text.sumOf { c -> if (c.isLowerCase()) 1 else 2 } }?.tag
        ?: tokens.first().tag

    return MToken(
        text = text,
        tag = tag,
        whitespace = tokens.last().whitespace,
        phonemes = phonemesStr,
        startTs = tokens.first().startTs,
        endTs = tokens.last().endTs,
        underscore = MToken.Underscore(
            isHead = tokens.first().underscore.isHead,
            alias = null,
            stress = if (stresses.size == 1) stresses.first() else null,
            currency = currencies.maxOrNull(), // String comparison works for simple cases
            numFlags = tokens.flatMap { it.underscore.numFlags.toList() }.distinct().sorted().joinToString(""),
            prespace = tokens.first().underscore.prespace,
            rating = if (null in ratings) null else ratings.filterNotNull().minOrNull()
        )
    )
}

fun stressWeight(ps: String?): Int {
    return ps?.sumOf { if (it in MisakiG2PConstants.DIPHTHONGS) 2 else 1 } ?: 0
}

fun applyStress(phonemesIn: String?, stressValue: Float?): String? {
    var ps = phonemesIn ?: return null
    if (stressValue == null) return ps

    fun restressInternal(s: String, newStressChar: Char): String {
        val builder = StringBuilder()
        var firstVowelFound = false
        var stressPlaced = false
        for (char_idx in s.indices) {
            val char = s[char_idx]
            if (!stressPlaced && char in MisakiG2PConstants.VOWELS) {
                firstVowelFound = true
                builder.append(newStressChar)
                builder.append(char)
                stressPlaced = true
            } else if (char !in MisakiG2PConstants.STRESSES) { // remove existing stresses
                builder.append(char)
            }
        }
        return if (!firstVowelFound && !stressPlaced) newStressChar + builder.toString() // Stress at start if no vowel
        else if (firstVowelFound && !stressPlaced) newStressChar + builder.toString() // Should not happen if logic correct
        else builder.toString()
    }


    return when {
        stressValue < -1.0f -> ps.replace(MisakiG2PConstants.PRIMARY_STRESS.toString(), "").replace(MisakiG2PConstants.SECONDARY_STRESS.toString(), "")
        stressValue == -1.0f || (stressValue in listOf(0.0f, -0.5f) && MisakiG2PConstants.PRIMARY_STRESS in ps) ->
            ps.replace(MisakiG2PConstants.SECONDARY_STRESS.toString(), "")
                .replace(MisakiG2PConstants.PRIMARY_STRESS, MisakiG2PConstants.SECONDARY_STRESS)
        stressValue in listOf(0.0f, 0.5f, 1.0f) && MisakiG2PConstants.STRESSES.none { it in ps } -> {
            if (MisakiG2PConstants.VOWELS.none { it in ps }) ps else restressInternal(ps, MisakiG2PConstants.SECONDARY_STRESS)
        }
        stressValue >= 1.0f && MisakiG2PConstants.PRIMARY_STRESS !in ps && MisakiG2PConstants.SECONDARY_STRESS in ps ->
            ps.replace(MisakiG2PConstants.SECONDARY_STRESS, MisakiG2PConstants.PRIMARY_STRESS)
        stressValue > 1.0f && MisakiG2PConstants.STRESSES.none { it in ps } -> {
            if (MisakiG2PConstants.VOWELS.none { it in ps }) ps else restressInternal(ps, MisakiG2PConstants.PRIMARY_STRESS)
        }
        else -> ps
    }
}


fun isDigit(text: String): Boolean {
    return text.isNotEmpty() && text.all { it.isDigit() }
}

fun subtokenize(word: String): List<String> {
    return MisakiG2PConstants.SUBTOKEN_REGEX.findAll(word).map { it.value }.toList()
}