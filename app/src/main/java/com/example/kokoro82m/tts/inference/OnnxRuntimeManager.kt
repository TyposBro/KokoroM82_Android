// Adopted from: https://github.com/puff-dayo/Kokoro-82M-Android


package com.example.kokoro82m.tts.inference

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import android.content.Context
import android.util.Log
import com.example.kokoro82m.R // Ensure this is your correct R file import
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object OnnxRuntimeManager {
    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private const val MODEL_FILE_NAME = "kokoro_full_model.onnx" // Give a distinct name for the cached model

    @Synchronized
    fun initialize(context: Context) {
        if (environment == null) {
            try {
                environment = OrtEnvironment.getEnvironment()
                session = createSession(context)
            } catch (e: Exception) {
                Log.e("OnnxRuntimeManager", "Error initializing ONNX Runtime: ${e.message}", e)
                // Optionally, rethrow or handle this error more gracefully in your UI
                throw e // Rethrow to make it visible during development
            }
        }
    }

    private fun copyModelToCache(context: Context): File {
        val modelFile = File(context.cacheDir, MODEL_FILE_NAME)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            if (modelFile.exists()) {
                Log.d("OnnxRuntimeManager", "Model already exists in cache: ${modelFile.absolutePath}")
                // Optional: Add a version check here if your model in res/raw can change
                // For now, we assume if it exists, it's the correct one.
                return modelFile
            }

            Log.d("OnnxRuntimeManager", "Copying model to cache: ${modelFile.absolutePath}")
            inputStream = context.resources.openRawResource(R.raw.kokoro) // Your model in res/raw
            outputStream = FileOutputStream(modelFile)
            val buffer = ByteArray(4 * 1024) // 4K buffer
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            Log.d("OnnxRuntimeManager", "Model copied successfully.")
        } catch (e: IOException) {
            Log.e("OnnxRuntimeManager", "Failed to copy model to cache", e)
            // Clean up partially written file if copy failed
            if (modelFile.exists()) {
                modelFile.delete()
            }
            throw e // Rethrow to indicate failure
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                Log.e("OnnxRuntimeManager", "Error closing streams", e)
            }
        }
        return modelFile
    }

    private fun createSession(context: Context): OrtSession {
        val options = SessionOptions().apply {
            // Your existing options
            addConfigEntry("nnapi.flags", "USE_FP16")
            addConfigEntry("nnapi.use_gpu", "true") // Ensure NNAPI is available and model supports it
            addConfigEntry("nnapi.gpu_precision_loss_allowed", "true")
            // Consider adding optimization levels if needed, e.g.:
            // setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ORT_ENABLE_ALL)
        }

        // Copy the model to internal storage and get its path
        val modelFile = copyModelToCache(context.applicationContext) // Use applicationContext

        Log.d("OnnxRuntimeManager", "Creating session from model path: ${modelFile.absolutePath}")
        // Create the session using the file path
        return environment!!.createSession(modelFile.absolutePath, options)
    }

    fun getSession() = requireNotNull(session) { "ONNX Session not initialized" }

    // Optional: Add a method to close the session if MainViewModel is ever designed to do so
    @Synchronized
    fun close() {
        try {
            session?.close()
            environment?.close() // Close environment when completely done
        } catch (e: Exception) {
            Log.e("OnnxRuntimeManager", "Error closing ONNX session/environment", e)
        } finally {
            session = null
            environment = null
        }
    }
}