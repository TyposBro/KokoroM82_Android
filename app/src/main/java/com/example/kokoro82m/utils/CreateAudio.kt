// Adopted from: https://github.com/puff-dayo/Kokoro-82M-Android


package com.example.kokoro82m.utils

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.kokoro82m.tts.inference.StyleLoader
import com.example.kokoro82m.tts.inference.Tokenizer

fun createAudio(
    phonemes: String,
    voice: String,
    speed: Float,
    session: OrtSession,
    context: Context,
): Pair<FloatArray, Int> {
    val MAX_PHONEME_LENGTH = 400
    val SAMPLE_RATE = 22050

    if (phonemes.length > MAX_PHONEME_LENGTH) {
        Log.w("Kokoro", "Phonemes are too long, truncating to $MAX_PHONEME_LENGTH phonemes")
    }
    val truncatedPhonemes = phonemes.take(MAX_PHONEME_LENGTH)

    val tokens = Tokenizer.Companion.tokenize(truncatedPhonemes)
    if (tokens.size > MAX_PHONEME_LENGTH) {
        throw IllegalArgumentException("Context length is $MAX_PHONEME_LENGTH, but leave room for the pad token 0 at the start & end")
    }

    val styleLoader = StyleLoader(context)
//    val styleArray = styleLoader.getStyleArray(voice)
    val styleIndex = tokens.size
    val styleArray = styleLoader.getStyleArray(name = voice, index = styleIndex)

    val paddedTokens = listOf(0L) + tokens.toList() + listOf(0L)
    val tokenTensor = OnnxTensor.createTensor(
        OrtEnvironment.getEnvironment(),
        arrayOf(paddedTokens.toLongArray())
    )
    val styleTensor = OnnxTensor.createTensor(
        OrtEnvironment.getEnvironment(),
        styleArray
    )
    val speedTensor = OnnxTensor.createTensor(
        OrtEnvironment.getEnvironment(),
        floatArrayOf(speed)
    )

    val inputs = mapOf(
        "input_ids" to tokenTensor,
        "style" to styleTensor,
        "speed" to speedTensor
    )
    val results = session.run(inputs)
    val audioTensor = (results[0].value as Array<FloatArray>)[0]
    results.close()

    return Pair(audioTensor, SAMPLE_RATE)
}

fun createAudioFromStyleVector(
    phonemes: String,
    voice: Array<FloatArray>,
    speed: Float,
    session: OrtSession,
): Pair<FloatArray, Int> {
    val MAX_PHONEME_LENGTH = 400
    val SAMPLE_RATE = 22050

    if (phonemes.length > MAX_PHONEME_LENGTH) {
        Log.w("Kokoro", "Phonemes are too long, truncating to $MAX_PHONEME_LENGTH phonemes")
    }
    val truncatedPhonemes = phonemes.take(MAX_PHONEME_LENGTH)

    val tokens = Tokenizer.Companion.tokenize(truncatedPhonemes)
    if (tokens.size > MAX_PHONEME_LENGTH) {
        throw IllegalArgumentException("Context length is $MAX_PHONEME_LENGTH, but leave room for the pad token 0 at the start & end")
    }

    val paddedTokens = listOf(0L) + tokens.toList() + listOf(0L)
    val tokenTensor = OnnxTensor.createTensor(
        OrtEnvironment.getEnvironment(),
        arrayOf(paddedTokens.toLongArray())
    )
    val styleTensor = OnnxTensor.createTensor(
        OrtEnvironment.getEnvironment(),
        voice
    )

    val speedTensor = OnnxTensor.createTensor(
        OrtEnvironment.getEnvironment(),
        floatArrayOf(speed)
    )

    val inputs = mapOf(
        "input_ids" to tokenTensor,
        "style" to styleTensor,
        "speed" to speedTensor
    )
    val results = session.run(inputs)
    val audioTensor = (results[0].value as Array<FloatArray>)[0]
    results.close()

    return Pair(audioTensor, SAMPLE_RATE)
}