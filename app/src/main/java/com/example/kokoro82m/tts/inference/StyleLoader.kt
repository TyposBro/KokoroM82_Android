// Adopted from: https://github.com/puff-dayo/Kokoro-82M-Android

package com.example.kokoro82m.tts.inference // Ensure this matches your package structure

import android.content.Context
import com.example.kokoro82m.names
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StyleLoader(private val context: Context) {



    private val styleResourceMap: Map<String, Int> = names.associateWith { name ->
        val resourceId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resourceId == 0) {
            throw IllegalArgumentException("Resource binary file '$name' not found in /res/raw. Ensure binary voice files are present with these exact names and no extensions.")
        }
        resourceId
    }

    fun getStyleArray(name: String, index: Int = 0): Array<FloatArray> {
        val resourceId = styleResourceMap[name]
            ?: throw IllegalArgumentException("Style '$name' not found. Ensure it's in the 'names' list and the corresponding binary file exists in /res/raw.")

        val inputStream: InputStream = context.resources.openRawResource(resourceId)
        val bytes: ByteArray = inputStream.use { it.readBytes() }

        // --- Configuration for your style vectors (elements per vector) ---
        val floatsPerVector = 256
        val bytesPerFloat = 4
        // --- End Configuration ---

        // Dynamically calculate the number of vectors based on file size
        if (bytes.size % (floatsPerVector * bytesPerFloat) != 0) {
            throw IllegalArgumentException(
                "Binary voice file '$name' has a size (${bytes.size} bytes) that is not a " +
                        "multiple of the expected vector size in bytes (${floatsPerVector * bytesPerFloat} bytes)."
            )
        }
        val numVectorsInFile = bytes.size / (floatsPerVector * bytesPerFloat)

        if (numVectorsInFile == 0) {
            throw IllegalArgumentException(
                "Binary voice file '$name' appears to be empty or too small to contain any vectors."
            )
        }

        // The 'index' parameter selects which of the 'numVectorsInFile' to load.
        if (index < 0 || index >= numVectorsInFile) {
            throw IllegalArgumentException(
                "Index ($index) is out of bounds for file '$name' which contains $numVectorsInFile vectors (valid range: 0 to ${numVectorsInFile - 1})."
            )
        }

        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floatBuffer = byteBuffer.asFloatBuffer()

        val styleArray = Array(1) { FloatArray(floatsPerVector) }

        val floatOffsetForSelectedVector = index * floatsPerVector

        floatBuffer.position(floatOffsetForSelectedVector)
        floatBuffer.get(styleArray[0], 0, floatsPerVector)

        return styleArray
    }
}