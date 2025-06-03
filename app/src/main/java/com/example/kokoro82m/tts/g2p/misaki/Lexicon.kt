package com.example.kokoro82m.tts.g2p.misaki


import org.json.JSONObject // Or your chosen JSON library
import java.io.InputStream
import java.text.Normalizer

class Lexicon(
    private val british: Boolean,
    goldJsonStream: InputStream, // Pass InputStream for resources
    silverJsonStream: InputStream
) {
    private val capStresses = Pair(0.5f, 2.0f)
    private val golds: Map<String, Any> // String or Map<String, String?>
    private val silvers: Map<String, Any>
    private val vocab: Set<Char>

    init {
        golds = growDictionary(parseJsonToMap(goldJsonStream.bufferedReader().readText()))
        silvers = growDictionary(parseJsonToMap(silverJsonStream.bufferedReader().readText()))
        vocab = if (british) MisakiG2PConstants.GB_VOCAB else MisakiG2PConstants.US_VOCAB
        validateDictionary(golds, "gold")
    }

    private fun parseJsonToMap(jsonString: String): Map<String, Any> {
        val resultMap = mutableMapOf<String, Any>()
        val jsonObject = JSONObject(jsonString)
        jsonObject.keys().forEach { key ->
            when (val value = jsonObject.get(key)) {
                is String -> resultMap[key] = value
                is JSONObject -> {
                    val innerMap = mutableMapOf<String, String?>()
                    value.keys().forEach { innerKey ->
                        innerMap[innerKey] = if (value.isNull(innerKey)) null else value.getString(innerKey)
                    }
                    resultMap[key] = innerMap.toMap()
                }
            }
        }
        return resultMap.toMap()
    }

    private fun validateDictionary(dict: Map<String, Any>, name: String) {
        dict.values.forEach { v ->
            when (v) {
                is String -> require(v.all { it in vocab }) { "Invalid phoneme in $name: $v" }
                is Map<*, *> -> {
                    require("DEFAULT" in v) { "Missing DEFAULT in $name entry: $v" }
                    v.values.forEach { subV ->
                        if (subV is String) require(subV.all { it in vocab }) { "Invalid phoneme in $name variant: $subV" }
                    }
                }
                else -> throw IllegalArgumentException("Unexpected type in $name dictionary: $v")
            }
        }
    }

    private fun growDictionary(d: Map<String, Any>): Map<String, Any> {
        val e = mutableMapOf<String, Any>()
        d.forEach { (k, v) ->
            if (k.length >= 2) {
                if (k == k.toLowerCase()) {
                    val capitalized = k.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                    if (k != capitalized) e[capitalized] = v
                } else if (k == k.toLowerCase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }) {
                    e[k.toLowerCase()] = v
                }
            }
        }
        return e + d
    }

    private fun getNNP(word: String): Pair<String?, Int?> {
        val psParts = word.filter { it.isLetter() }.map { (golds[it.toString().toUpperCase()] as? String) }
        if (null in psParts) return null to null

        var phonemes = applyStress(psParts.joinToString(""), 0.0f) ?: return null to null

        // Python: ps.rsplit(SECONDARY_STRESS, 1) -> PRIMARY_STRESS.join(ps)
        // This means if secondary stress is present, replace the *last* one with primary.
        // If not, and needs primary, add it.
        val lastSecondary = phonemes.lastIndexOf(MisakiG2PConstants.SECONDARY_STRESS)
        if (lastSecondary != -1) {
            phonemes = phonemes.substring(0, lastSecondary) +
                    MisakiG2PConstants.PRIMARY_STRESS +
                    phonemes.substring(lastSecondary + 1)
        } else if (MisakiG2PConstants.PRIMARY_STRESS !in phonemes && MisakiG2PConstants.VOWELS.any { it in phonemes }) {
            // Simplified: add primary stress at the beginning if no stress and has vowels
            // The original `apply_stress(..., 0)` and then `rsplit` implies more complex logic.
            // For NNP, it usually implies primary stress on a prominent syllable.
            var tempPhonemes = MisakiG2PConstants.PRIMARY_STRESS + phonemes
            // Quick check to move stress before first vowel if it's just added at start
            val firstVowelIdx = tempPhonemes.indexOfFirst { it in MisakiG2PConstants.VOWELS }
            if (firstVowelIdx > 0 && tempPhonemes[0] == MisakiG2PConstants.PRIMARY_STRESS) { //
                phonemes = tempPhonemes.substring(1, firstVowelIdx) + MisakiG2PConstants.PRIMARY_STRESS + tempPhonemes.substring(firstVowelIdx)
            } else {
                phonemes = tempPhonemes
            }
        }
        return phonemes to 3
    }


    private fun getSpecialCase(word: String, tag: String, stress: Float?, ctx: TokenContext?): Pair<String?, Int?> {
        // ctx can be null if called from getNumber
        val currentCtx = ctx ?: TokenContext() // Default context if null

        if (tag == "ADD" && word in MisakiG2PConstants.ADD_SYMBOLS) {
            return lookup(MisakiG2PConstants.ADD_SYMBOLS[word]!!, null, -0.5f, currentCtx)
        }
        if (word in MisakiG2PConstants.SYMBOLS) {
            return lookup(MisakiG2PConstants.SYMBOLS[word]!!, null, null, currentCtx)
        }
        if ('.' in word.trim('.') && word.replace(".", "").all { it.isLetter() } &&
            (word.split('.').maxByOrNull { it.length }?.length ?: 0) < 3) {
            return getNNP(word)
        }
        // ... (rest of the when block from Python, translated to Kotlin if/else or when)
        when (word) {
            "a", "A" -> return (if (tag == "DT") "ɐ" else "ˈA") to 4
            "am", "Am", "AM" -> {
                if (tag.startsWith("NN")) return getNNP(word)
                return (if (currentCtx.futureVowel == null || word != "am" || (stress != null && stress > 0)) {
                    golds["am"] as? String
                } else "ɐm") to 4
            }
            "an", "An", "AN" -> {
                if (word == "AN" && tag.startsWith("NN")) return getNNP(word)
                return "ɐn" to 4
            }
            "I" -> if (tag == "PRP") return "${MisakiG2PConstants.SECONDARY_STRESS}I" to 4
            "by", "By", "BY" -> if (getParentTag(tag) == "ADV") return "bˈI" to 4
            "to", "To", "TO" -> if (word == "TO" && tag !in listOf("TO", "IN")) { /* no op */ } else {
                return (when(currentCtx.futureVowel) { null -> golds["to"]; false -> "tə"; true -> "tʊ" } as? String) to 4
            }
            "in", "In", "IN" -> if (word == "IN" && tag == "NNP") { /* no op, NNP handles it */ } else {
                val effectiveStress = if (currentCtx.futureVowel == null || tag != "IN") MisakiG2PConstants.PRIMARY_STRESS else ""
                return (effectiveStress + "ɪn") to 4
            }
            "the", "The", "THE" -> if (word == "THE" && tag != "DT") { /* no op */ } else {
                return (if (currentCtx.futureVowel == true) "ði" else "ðə") to 4
            }
            "used", "Used", "USED" -> {
                val usedEntry = golds["used"] as? Map<String, String>
                return (if (tag in listOf("VBD", "JJ") && currentCtx.futureTo) {
                    usedEntry?.get("VBD")
                } else usedEntry?.get("DEFAULT")) to 4
            }
        }
        if (tag == "IN" && word.matches(Regex("(?i)vs\\.?$"))) {
            return lookup("versus", null, null, currentCtx)
        }
        return null to null
    }

    private fun getParentTag(tag: String?): String? {
        return when {
            tag == null -> null
            tag.startsWith("VB") -> "VERB" // Verbs
            tag.startsWith("NN") -> "NOUN" // Nouns
            tag.startsWith("RB") || tag == "ADV" -> "ADV" // Adverbs (RB is a common tag for adverbs)
            tag.startsWith("JJ") || tag == "ADJ" -> "ADJ" // Adjectives
            else -> tag
        }
    }

    fun isKnown(word: String, tag: String?): Boolean {
        if (word in golds || word in MisakiG2PConstants.SYMBOLS || word in silvers) return true
        if (!word.all { it.isLetter() } || !word.all { it.code in MisakiG2PConstants.LEXICON_ORDS }) {
            return false
        }
        if (word.length == 1) return true
        if (word == word.toUpperCase() && word.toLowerCase() in golds) return true
        return if (word.length > 1) word.substring(1) == word.substring(1).toUpperCase() else false
    }

    fun lookup(word: String, tag: String?, stress: Float?, ctx: TokenContext?): Pair<String?, Int?> {
        var currentWord = word
        var isNNP: Boolean? = null
        if (currentWord == currentWord.toUpperCase() && currentWord !in golds) {
            currentWord = currentWord.toLowerCase()
            isNNP = (tag == "NNP")
        }

        var psEntry: Any? = golds[currentWord]
        var rating: Int? = 4
        if (psEntry == null && isNNP != true) {
            psEntry = silvers[currentWord]
            rating = 3
        }

        var psString: String? = null
        if (psEntry is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val psDict = psEntry as Map<String, String?>
            val effectiveTagKey = when {
                ctx?.futureVowel == null && "None" in psDict -> "None"
                tag !in psDict -> getParentTag(tag)
                else -> tag
            }
            psString = psDict[effectiveTagKey ?: "DEFAULT"] ?: psDict["DEFAULT"]
        } else if (psEntry is String) {
            psString = psEntry
        }

        if (psString == null || (isNNP == true && MisakiG2PConstants.PRIMARY_STRESS !in (psString ?: ""))) {
            val (nnpPs, nnpRating) = getNNP(word)
            if (nnpPs != null) return applyStress(nnpPs, stress) to nnpRating
        }
        return applyStress(psString, stress) to rating
    }

    private fun _s(stem: String?): String? {
        if (stem.isNullOrEmpty()) return null
        return when (stem.last()) {
            in listOf('p', 't', 'k', 'f', 'θ') -> stem + "s"
            in listOf('s', 'z', 'ʃ', 'ʒ', 'ʧ', 'ʤ') -> stem + (if (british) "ɪ" else "ᵻ") + "z"
            else -> stem + "z"
        }
    }

    fun stemS(word: String, tag: String?, stress: Float?, ctx: TokenContext): Pair<String?, Int?> {
        if (word.length < 3 || !word.endsWith('s')) return null to null
        val stemWord = when {
            !word.endsWith("ss") && isKnown(word.dropLast(1), tag) -> word.dropLast(1)
            (word.endsWith("'s") || (word.length > 4 && word.endsWith("es") && !word.endsWith("ies"))) && isKnown(word.dropLast(2), tag) -> word.dropLast(2)
            word.length > 4 && word.endsWith("ies") && isKnown(word.dropLast(3) + "y", tag) -> word.dropLast(3) + "y"
            else -> return null to null
        }
        val (stemPs, rating) = lookup(stemWord, tag, stress, ctx)
        return _s(stemPs) to rating
    }

    private fun _ed(stem: String?): String? {
        if (stem.isNullOrEmpty()) return null
        return when {
            stem.last() in listOf('p', 'k', 'f', 'θ', 'ʃ', 's', 'ʧ') -> stem + "t"
            stem.last() == 'd' -> stem + (if (british) "ɪ" else "ᵻ") + "d"
            stem.last() != 't' -> stem + "d"
            // stem.last() == 't'
            british || stem.length < 2 -> stem + "ɪd"
            stem[stem.length - 2] in MisakiG2PConstants.US_TAUS -> stem.dropLast(1) + "ɾᵻd"
            else -> stem + "ᵻd"
        }
    }

    fun stemEd(word: String, tag: String?, stress: Float?, ctx: TokenContext): Pair<String?, Int?> {
        if (word.length < 4 || !word.endsWith('d')) return null to null
        val stemWord = when {
            !word.endsWith("dd") && isKnown(word.dropLast(1), tag) -> word.dropLast(1)
            word.length > 4 && word.endsWith("ed") && !word.endsWith("eed") && isKnown(word.dropLast(2), tag) -> word.dropLast(2)
            else -> return null to null
        }
        val (stemPs, rating) = lookup(stemWord, tag, stress, ctx)
        return _ed(stemPs) to rating
    }

    private fun _ing(stem: String?): String? {
        if (stem.isNullOrEmpty()) return null
        return when {
            british && stem.isNotEmpty() && stem.last() in listOf('ə', 'ː') -> null // Python: if stem.endswith('ring') and stem[-1] in 'əː'
            !british && stem.length > 1 && stem.last() == 't' && stem[stem.length - 2] in MisakiG2PConstants.US_TAUS ->
                stem.dropLast(1) + "ɾɪŋ"
            else -> stem + "ɪŋ"
        }
    }

    fun stemIng(word: String, tag: String?, stress: Float?, ctx: TokenContext): Pair<String?, Int?> {
        if (word.length < 5 || !word.endsWith("ing")) return null to null
        val stemWord = when {
            word.length > 5 && isKnown(word.dropLast(3), tag) -> word.dropLast(3)
            isKnown(word.dropLast(3) + "e", tag) -> word.dropLast(3) + "e"
            word.length > 5 && Regex("([bcdgklmnprstvxz])\\1ing$|cking$").containsMatchIn(word) && isKnown(word.dropLast(4), tag) ->
                word.dropLast(4)
            else -> return null to null
        }
        val (stemPs, rating) = lookup(stemWord, tag, stress, ctx)
        return _ing(stemPs) to rating
    }


    fun getWord(originalWord: String, tag: String?, stressIn: Float?, ctx: TokenContext): Pair<String?, Int?> {
        var word = originalWord
        val (psSpecial, ratingSpecial) = getSpecialCase(word, tag ?: "", stressIn, ctx)
        if (psSpecial != null) return psSpecial to ratingSpecial

        val wl = word.toLowerCase()
        if (word.length > 1 && word.replace("'", "").all { it.isLetter() } && word != word.toLowerCase() &&
            (tag != "NNP" || word.length > 7) && word !in golds && word !in silvers &&
            (word == word.toUpperCase() || (word.length > 1 && word.substring(1) == word.substring(1).toLowerCase())) &&
            (wl in golds || wl in silvers ||
                    listOf(::stemS, ::stemEd, ::stemIng).any { it(wl, tag, stressIn, ctx).first != null })
        ) {
            word = wl
        }
        // Determine stress based on casing if not provided
        val stress = stressIn ?: (if (word == word.toLowerCase()) null
        else (if (word == word.toUpperCase()) capStresses.second else capStresses.first))


        if (isKnown(word, tag)) {
            return lookup(word, tag, stress, ctx)
        }
        if (word.endsWith("s'") && isKnown(word.dropLast(2) + "'s", tag)) {
            return lookup(word.dropLast(2) + "'s", tag, stress, ctx)
        }
        if (word.endsWith("'") && isKnown(word.dropLast(1), tag)) {
            return lookup(word.dropLast(1), tag, stress, ctx)
        }

        val (sPs, sRating) = stemS(word, tag, stress, ctx)
        if (sPs != null) return sPs to sRating

        val (edPs, edRating) = stemEd(word, tag, stress, ctx)
        if (edPs != null) return edPs to edRating

        val effectiveStressForIng = stress ?: 0.5f // Python: 0.5 if stress is None else stress
        val (ingPs, ingRating) = stemIng(word, tag, effectiveStressForIng, ctx)
        if (ingPs != null) return ingPs to ingRating

        return null to null
    }

    private fun isCurrencyNum(word: String): Boolean {
        if ('.' !in word) return true
        if (word.count { it == '.' } > 1) return false
        val cents = word.split('.')[1]
        return cents.length < 3 || cents.all { it == '0' }
    }

    // Placeholder for num2words and its complex logic
    fun getNumber(word: String, currencySymbol: String?, isHead: Boolean, numFlags: String): Pair<String?, Int?> {
        // TODO_KOTLIN: Implement with your Java/Kotlin num2words equivalent
        // This function is highly dependent on num2words output and specific rules.
        // For demonstration, a very simplified placeholder:
        if (isDigit(word)) {
            val phonemes = word.mapNotNull { digitChar ->
                lookup(digitChar.toString(), "CD", -2.0f, TokenContext())?.first
            }.joinToString(" ")
            return if (phonemes.isNotBlank()) phonemes to 3 else null to null
        }
        println("Lexicon.getNumber: Placeholder for '$word', currency '$currencySymbol', flags '$numFlags'")
        return null to null // Fallback if not simple digits
    }

    private fun appendCurrency(ps: String?, currencyChar: String?): String? {
        if (ps == null || currencyChar.isNullOrEmpty()) return ps
        val currencyPair = MisakiG2PConstants.CURRENCIES[currencyChar.first()] ?: return ps
        val currencyNamePs = stemS(currencyPair.first + "s", null, null, TokenContext())?.first
            ?: lookup(currencyPair.first, null, null, TokenContext())?.first
        return if (currencyNamePs != null) "$ps $currencyNamePs" else ps
    }

    private fun numericIfNeeded(c: Char): Char {
        // NFKC normalization (done in invoke) should handle most of this.
        return c
    }

    fun isNumber(word: String, isHead: Boolean): Boolean {
        if (word.all { !it.isDigit() }) return false // Python used its own is_digit
        var tempWord = word
        val suffixes = listOf("ing", "'d", "ed", "'s") + MisakiG2PConstants.ORDINALS.toList() + listOf("s")
        for (s in suffixes) {
            if (tempWord.endsWith(s)) {
                tempWord = tempWord.removeSuffix(s)
                break
            }
        }
        return tempWord.all { it.isDigit() || it in ",." || (isHead && tempWord.indexOf(it) == 0 && it == '-') }
    }

    operator fun invoke(tk: MToken, ctx: TokenContext?): Pair<String?, Int?> {
        val currentCtx = ctx ?: TokenContext() // Ensure ctx is not null
        var word = tk.underscore.alias ?: tk.text
        word = word.replace("‘", "'").replace("’", "'")
        word = Normalizer.normalize(word, Normalizer.Form.NFKC)
        // numericIfNeeded was applied char by char in Python, NFKC handles this broadly.

        val baseStress = if (word == word.toLowerCase()) null
        else (if (word == word.toUpperCase()) capStresses.second else capStresses.first)
        val currentStress = tk.underscore.stress ?: baseStress

        val (ps, rating) = getWord(word, tk.tag, currentStress, currentCtx)

        if (ps != null) {
            return applyStress(appendCurrency(ps, tk.underscore.currency), tk.underscore.stress ?: baseStress) to rating
        }

        if (isNumber(word, tk.underscore.isHead)) {
            val (numPs, numRating) = getNumber(word, tk.underscore.currency, tk.underscore.isHead, tk.underscore.numFlags)
            return applyStress(numPs, tk.underscore.stress ?: baseStress) to numRating
        }
        if (word.any { it.code !in MisakiG2PConstants.LEXICON_ORDS }) {
            return null to null
        }
        return null to null
    }
}