package com.example.kokoro82m.tts.g2p.misaki


import java.io.InputStream

class G2P(
    val version: String? = null,
    val british: Boolean = false,
    val trf: Boolean = false, // For spaCy model type, adapt to your NLP lib
    fallbackNetworkProvider: (() -> FallbackNetwork)? = null,
    val unk: String = "❓",
    // Provide streams for lexicon JSON files
    goldJsonStream: InputStream,
    silverJsonStream: InputStream,
    // Provider for your SpaCy equivalent
    private val nlpProvider: NlpProvider // Define NlpProvider interface
) {
    interface NlpToken { // Your NLP library's token representation
        val text: String
        val tag: String // POS Tag
        val whitespace: String
        // Add other fields your NLP lib provides if needed for alignment/features
    }
    interface NlpProvider {
        fun tokenize(text: String): List<NlpToken>
        // If your NLP lib can do alignment similar to spaCy's Alignment.from_strings:
        // fun align(original: List<String>, tokenized: List<String>): AlignmentResult
    }

    private val nlp: NlpProvider = nlpProvider
    private val lexicon: Lexicon = Lexicon(british, goldJsonStream, silverJsonStream)
    private val fallback: FallbackNetwork? = fallbackNetworkProvider?.invoke()


    private fun preprocessText(text: String): Triple<String, List<String>, Map<Int, Any>> {
        var result = ""
        val tokens = mutableListOf<String>() // Original tokens for alignment
        val features = mutableMapOf<Int, Any>() // Key is index in `tokens` list
        var lastEnd = 0
        val cleanedText = text.trimStart()

        MisakiG2PConstants.LINK_REGEX.findAll(cleanedText).forEach { matchResult ->
            val pretext = cleanedText.substring(lastEnd, matchResult.range.first)
            result += pretext
            tokens.addAll(pretext.split(' ').filter { it.isNotEmpty() })

            val featureText = matchResult.groupValues[2]
            val featureValue: Any? = when {
                featureText.matches(Regex("^[+-]?\\d+$")) -> featureText.toIntOrNull()
                featureText == "0.5" || featureText == "+0.5" -> 0.5f
                featureText == "-0.5" -> -0.5f
                featureText.length > 1 && featureText.startsWith('/') && featureText.endsWith('/') ->
                    featureText.drop(1).dropLastWhile { it == '/' }
                featureText.length > 1 && featureText.startsWith('#') && featureText.endsWith('#') ->
                    featureText.drop(1).dropLastWhile { it == '#' }
                else -> null
            }
            if (featureValue != null) {
                features[tokens.size] = featureValue // Feature applies to the *next* token to be added
            }
            result += matchResult.groupValues[1]
            tokens.add(matchResult.groupValues[1]) // Add the actual token text
            lastEnd = matchResult.range.last + 1
        }

        if (lastEnd < cleanedText.length) {
            val postText = cleanedText.substring(lastEnd)
            result += postText
            tokens.addAll(postText.split(' ').filter { it.isNotEmpty() })
        }
        return Triple(result, tokens, features)
    }

    private fun applyFeaturesToTokens(
        nlpTokens: List<MToken>,
        originalPreprocessedTokens: List<String>,
        features: Map<Int, Any>
    ): List<MToken> {
        if (features.isEmpty()) return nlpTokens

        // TODO_KOTLIN: Implement alignment if your NLPProvider supports it.
        // Python: align = spacy.training.Alignment.from_strings(tokens, [tk.text for tk in mutable_tokens])
        // If no alignment, this simplified version assumes features apply to tokens by some heuristic.
        // This is a *critical* step for correctness if feature indices don't map directly.
        // For now, a placeholder that tries to match based on text if originalPreprocessedTokens were simple words.
        // This part highly depends on your NlpProvider's capabilities.

        val mutableNlpTokens = nlpTokens.toMutableList() // Work on a mutable copy

        var currentOriginalTokenIdx = 0
        features.toSortedMap().forEach { (featureApplicationPoint, featureValue) ->
            // Try to find the corresponding MToken. This is a simplification.
            // The original code aligns `originalPreprocessedTokens` with `nlpTokens`.
            // `featureApplicationPoint` is an index into `originalPreprocessedTokens`.
            // We need to find which MToken(s) correspond to originalPreprocessedTokens[featureApplicationPoint].

            // This loop attempts a basic alignment based on cumulative text.
            // It's NOT a robust replacement for spaCy's alignment.
            var matchedTokenIndexInNlp: Int? = null
            var cumulativeOriginalText = ""
            for(i in 0 until featureApplicationPoint) {
                cumulativeOriginalText += originalPreprocessedTokens.getOrNull(i) ?: ""
            }
            val targetOriginalTokenText = originalPreprocessedTokens.getOrNull(featureApplicationPoint)

            if(targetOriginalTokenText != null) {
                var cumulativeNlpText = ""
                for(j in mutableNlpTokens.indices) {
                    if(cumulativeNlpText.length >= cumulativeOriginalText.length) {
                        // Heuristic: if the MToken starts around where the original token started
                        // and its text matches, consider it. This is very rough.
                        if (mutableNlpTokens[j].text.startsWith(targetOriginalTokenText) || targetOriginalTokenText.startsWith(mutableNlpTokens[j].text)){
                            matchedTokenIndexInNlp = j
                            // Check `i` from Python: (i == 0 for is_head) needs context from alignment spans.
                            // For now, if we find one token, we apply it.
                            break
                        }
                    }
                    cumulativeNlpText += mutableNlpTokens[j].text + mutableNlpTokens[j].whitespace
                }
            }


            if (matchedTokenIndexInNlp != null && matchedTokenIndexInNlp < mutableNlpTokens.size) {
                val tokenToModify = mutableNlpTokens[matchedTokenIndexInNlp]
                // Original Python had `i` from `enumerate(np.where(align.y2x.data == k)[0])`
                // `is_head` was `i == 0`. This needs proper alignment data.
                // For simplicity, we'll assume the first token affected by a feature might be a head.
                val isFeatureHead = true // Placeholder for proper head detection from alignment

                when (featureValue) {
                    is Float -> tokenToModify.underscore.stress = featureValue
                    is Int -> tokenToModify.underscore.stress = featureValue.toFloat()
                    is String -> {
                        if (featureValue.startsWith('/')) {
                            if(isFeatureHead) tokenToModify.phonemes = featureValue.drop(1) else tokenToModify.phonemes = ""
                            tokenToModify.underscore.isHead = isFeatureHead
                            tokenToModify.underscore.rating = 5
                        } else if (featureValue.startsWith('#')) {
                            tokenToModify.underscore.numFlags = featureValue.drop(1)
                        }
                    }
                }
            } else {
                println("Warning: Could not reliably map feature at original index $featureApplicationPoint to an NLP token.")
            }
        }
        return mutableNlpTokens
    }


    private fun convertNlpToMTokens(nlpLibTokens: List<NlpToken>): List<MToken> {
        return nlpLibTokens.map { nlpTk ->
            MToken(
                text = nlpTk.text,
                tag = nlpTk.tag,
                whitespace = nlpTk.whitespace,
                underscore = MToken.Underscore(isHead = true, numFlags = "", prespace = false) // Initial defaults
            )
        }
    }


    private fun foldLeft(tokens: List<MToken>): List<MToken> {
        val result = mutableListOf<MToken>()
        tokens.forEach { tk ->
            if (result.isNotEmpty() && !tk.underscore.isHead) {
                val last = result.removeLast()
                result.add(mergeTokens(listOf(last, tk), unk = unk))
            } else {
                result.add(tk.copy()) // Use copy to avoid modifying input list elements
            }
        }
        return result
    }

    private fun retokenize(tokens: List<MToken>): List<Any> { // List of MToken or MutableList<MToken>
        val words = mutableListOf<Any>()
        var currentCurrencySymbol: String? = null

        tokens.forEachIndexed { tokenIndex, currentMToken ->
            val mTokenCopy = currentMToken.copy(underscore = currentMToken.underscore.copy()) // Work on a copy

            val subtokensToProcess: List<MToken> =
                if (mTokenCopy.underscore.alias == null && mTokenCopy.phonemes == null) {
                    subtokenize(mTokenCopy.text).mapIndexed { idx, subText ->
                        mTokenCopy.copy( // Create new MToken for each subtoken
                            text = subText,
                            whitespace = if (idx == subtokenize(mTokenCopy.text).size -1) mTokenCopy.whitespace else "", // only last gets whitespace
                            underscore = mTokenCopy.underscore.copy(isHead = true, prespace = false)
                        )
                    }
                } else {
                    listOf(mTokenCopy)
                }

            if (subtokensToProcess.isEmpty()) return@forEachIndexed // Skip if subtokenization results in empty

            // Last subtoken of the original token gets the original token's whitespace
            // This was done inside mapIndexed above.

            subtokensToProcess.forEachIndexed { subtokenIdx, tk ->
                // tk is a copy, can be modified
                if (tk.underscore.alias != null || tk.phonemes != null) {
                    // Already processed
                } else if (tk.tag == "$" && tk.text in MisakiG2PConstants.CURRENCIES) {
                    currentCurrencySymbol = tk.text
                    tk.phonemes = ""
                    tk.underscore.rating = 4
                } else if (tk.tag == ":" && tk.text in listOf("-", "–")) {
                    tk.phonemes = "—"
                    tk.underscore.rating = 3
                } else if (tk.tag in MisakiG2PConstants.PUNCT_TAGS && !tk.text.all { it.isLetterOrDigit() }) {
                    tk.phonemes = MisakiG2PConstants.PUNCT_TAG_PHONEMES[tk.tag] ?: tk.text.filter { it in MisakiG2PConstants.PUNCTS }
                    tk.underscore.rating = 4
                } else if (currentCurrencySymbol != null) {
                    if (tk.tag != "CD") { // CD: Cardinal number
                        currentCurrencySymbol = null
                    } else if (subtokenIdx + 1 == subtokensToProcess.size &&
                        (tokenIndex + 1 == tokens.size || tokens.getOrNull(tokenIndex + 1)?.tag != "CD")
                    ) {
                        tk.underscore.currency = currentCurrencySymbol
                        // currentCurrencySymbol = null; // Python version did not reset here inside subtoken loop
                    }
                } else if (subtokenIdx > 0 && subtokenIdx < subtokensToProcess.size - 1 &&
                    tk.text == "2" &&
                    (subtokensToProcess.getOrNull(subtokenIdx - 1)?.text?.lastOrNull()?.isLetter() == true) &&
                    (subtokensToProcess.getOrNull(subtokenIdx + 1)?.text?.firstOrNull()?.isLetter() == true)
                ) {
                    tk.underscore.alias = "to"
                }

                // Grouping logic
                if (tk.underscore.alias != null || tk.phonemes != null) {
                    words.add(tk)
                } else if (words.isNotEmpty() && words.last() is MutableList<*> &&
                    ((words.last() as List<MToken>).lastOrNull()?.whitespace?.isEmpty() == true)) {
                    tk.underscore.isHead = false
                    @Suppress("UNCHECKED_CAST")
                    (words.last() as MutableList<MToken>).add(tk)
                } else {
                    if (tk.whitespace.isNotEmpty()) words.add(tk) else words.add(mutableListOf(tk))
                }
            }
        }
        return words.map { if (it is List<*> && it.size == 1) it.first()!! else it }
    }


    private fun determineContext(currentCtx: TokenContext, phonemes: String?, token: MToken): TokenContext {
        var newVowelState = currentCtx.futureVowel
        if (phonemes != null) {
            newVowelState = phonemes.firstOrNull { char ->
                char in MisakiG2PConstants.VOWELS ||
                        char in MisakiG2PConstants.CONSONANTS ||
                        char in MisakiG2PConstants.NON_QUOTE_PUNCTS
            }?.let { relevantChar ->
                if (relevantChar in MisakiG2PConstants.NON_QUOTE_PUNCTS) null else (relevantChar in MisakiG2PConstants.VOWELS)
            } ?: newVowelState // Keep old if no relevant char found
        }
        val newFutureTo = token.text in listOf("to", "To") || (token.text == "TO" && token.tag in listOf("TO", "IN"))
        return TokenContext(futureVowel = newVowelState, futureTo = newFutureTo)
    }

    private fun resolveGroupTokens(tokensInGroup: MutableList<MToken>) {
        val fullText = tokensInGroup.joinToString("") { it.text + it.whitespace }
        val hasInternalSpaceOrSlash = ' ' in fullText || '/' in fullText
        val hasMixedCharTypes = tokensInGroup.mapNotNull { mtk ->
            mtk.text.firstOrNull { char -> char !in MisakiG2PConstants.SUBTOKEN_JUNKS }
                ?.let { relevantChar ->
                    when {
                        relevantChar.isLetter() -> 0
                        relevantChar.isDigit() -> 1
                        else -> 2
                    }
                }
        }.distinct().size > 1
        val effectivePrespace = hasInternalSpaceOrSlash || hasMixedCharTypes

        tokensInGroup.forEachIndexed { i, tk ->
            if (tk.phonemes == null) { // Only modify if not already set
                if (i == tokensInGroup.size - 1 && tk.text.length == 1 && tk.text.first() in MisakiG2PConstants.NON_QUOTE_PUNCTS) {
                    tk.phonemes = tk.text
                    tk.underscore.rating = 3
                } else if (tk.text.all { it in MisakiG2PConstants.SUBTOKEN_JUNKS }) {
                    tk.phonemes = ""
                    tk.underscore.rating = 3
                }
            }
            // Set prespace for subsequent tokens in the group if phonemes were set (by lexicon or above rules)
            if (i > 0 && tk.phonemes != null) { // Python code checks if tk.phonemes is truthy (not None, not empty)
                tk.underscore.prespace = effectivePrespace
            }
        }

        if (effectivePrespace) return // No stress adjustment if considered to have internal spacing

        val stressInfo = tokensInGroup.mapIndexedNotNull { i, tk ->
            tk.phonemes?.let { Triple(MisakiG2PConstants.PRIMARY_STRESS in it, stressWeight(it), i) }
        }

        if (stressInfo.size == 2 && tokensInGroup.getOrNull(stressInfo[0].third)?.text?.length == 1) {
            val tokenToAdjust = tokensInGroup.getOrNull(stressInfo[1].third)
            tokenToAdjust?.phonemes = applyStress(tokenToAdjust?.phonemes, -0.5f)
            return
        }
        if (stressInfo.size < 2 || stressInfo.count { it.first } <= (stressInfo.size + 1) / 2) {
            return // Not enough primary stresses or items to adjust
        }

        // Demote stress on some tokens
        val sortedForDemotion = stressInfo.sortedWith(compareBy({ !it.first }, { it.second })) // primary stress false first
        val toDemoteCount = stressInfo.size / 2
        sortedForDemotion.take(toDemoteCount).forEach { (_, _, index) ->
            tokensInGroup.getOrNull(index)?.let {
                it.phonemes = applyStress(it.phonemes, -0.5f)
            }
        }
    }

    operator fun invoke(text: String, enablePreprocessing: Boolean = true): Pair<String, List<MToken>> {
        val (processedText, originalTokensForAlignment, features) = if (enablePreprocessing) {
            preprocessText(text)
        } else {
            Triple(text, emptyList<String>(), emptyMap<Int, Any>())
        }

        val nlpLibTokens = nlp.tokenize(processedText)
        var mTokens = convertNlpToMTokens(nlpLibTokens)
        mTokens = applyFeaturesToTokens(mTokens, originalTokensForAlignment, features)

        mTokens = foldLeft(mTokens)
        val retokenizedItems = retokenize(mTokens) // List of MToken or MutableList<MToken>

        var currentCtx = TokenContext()

        for (i in retokenizedItems.indices.reversed()) {
            when (val item = retokenizedItems[i]) {
                is MToken -> {
                    if (item.phonemes == null) { // Process if not already phonemized (e.g. by feature)
                        val (ps, rating) = lexicon(item, currentCtx)
                        item.phonemes = ps
                        item.underscore.rating = rating
                    }
                    if (item.phonemes == null && fallback != null) {
                        val (ps, rating) = fallback.invoke(item)
                        item.phonemes = ps
                        item.underscore.rating = rating
                    }
                    currentCtx = determineContext(currentCtx, item.phonemes, item)
                }
                is MutableList<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val tokenGroup = item as MutableList<MToken> // These are copies from retokenize
                    var groupHandledByLexicon = false

                    // Try to phonemize the whole group as one unit first
                    if (tokenGroup.none { it.underscore.alias != null || it.phonemes != null }) {
                        val mergedForLexiconAttempt = mergeTokens(tokenGroup) // unk not needed for lexicon lookup
                        val (ps, rating) = lexicon(mergedForLexiconAttempt, currentCtx)
                        if (ps != null) {
                            tokenGroup.first().phonemes = ps
                            tokenGroup.first().underscore.rating = rating
                            tokenGroup.drop(1).forEach { it.phonemes = ""; it.underscore.rating = rating }
                            currentCtx = determineContext(currentCtx, ps, mergedForLexiconAttempt)
                            groupHandledByLexicon = true
                        }
                    }

                    if (!groupHandledByLexicon) {
                        var needsFallbackForGroup = false
                        tokenGroup.forEach { tkInGroup -> // Check if any sub-token needs fallback
                            if (tkInGroup.phonemes == null && tkInGroup.underscore.alias == null) {
                                if (tkInGroup.text.all { it in MisakiG2PConstants.SUBTOKEN_JUNKS }) {
                                    tkInGroup.phonemes = ""
                                    tkInGroup.underscore.rating = 3
                                } else {
                                    // A non-junk subtoken that couldn't be resolved by itself earlier in retokenize
                                    // and the group couldn't be resolved by lexicon.
                                    needsFallbackForGroup = true
                                }
                            }
                        }
                        if (needsFallbackForGroup && fallback != null) {
                            val mergedForFallback = mergeTokens(tokenGroup)
                            val (ps, rating) = fallback.invoke(mergedForFallback)
                            tokenGroup.first().phonemes = ps
                            tokenGroup.first().underscore.rating = rating
                            tokenGroup.drop(1).forEach { it.phonemes = ""; it.underscore.rating = rating }
                            // currentCtx update based on fallback result might be needed if complex
                        } else if (!needsFallbackForGroup) { // Not handled by lexicon, not by fallback (or no fallback)
                            resolveGroupTokens(tokenGroup) // Apply internal stress rules etc.
                        }
                    }
                }
            }
        }

        val finalTokens = retokenizedItems.map { item ->
            when (item) {
                is MToken -> item
                is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    mergeTokens(item as List<MToken>, unk = unk)
                }
                else -> throw IllegalStateException("Unexpected item type in retokenized list: $item")
            }
        }

        if (version != "2.0") {
            finalTokens.forEach { tk ->
                tk.phonemes = tk.phonemes?.replace('ɾ', 'T')?.replace('ʔ', 't')
            }
        }

        val resultPhonemeString = finalTokens.joinToString("") { (it.phonemes ?: unk) + it.whitespace }
        return resultPhonemeString to finalTokens
    }
}