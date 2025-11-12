package com.example.moodtrackerapp

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmotionClassifier(
    context: Context,
    modelAssetName: String = "emotion.tflite",
    // IMPORTANT: order must match your model's output!
    private val labels: List<String> = listOf(
        "angry", "disgust", "fear", "happy", "sad", "surprise", "neutral"
    ),
    private val inputSize: Int = 48 // expected width & height
) {

    companion object {
        private const val TAG = "EmotionClassifier"

        // Tweak this if needed after inspecting logs:
        private const val NORMALIZE_ZERO_ONE = 0      // [0,1]
        private const val NORMALIZE_MINUS_ONE_ONE = 1 // [-1,1]

        // Default assumption for most FER 48x48 models:
        private const val NORMALIZATION_MODE = NORMALIZE_ZERO_ONE
    }

    private val interpreter: Interpreter

    init {
        interpreter = try {
            val bb = loadModelFile(context, modelAssetName)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            Interpreter(bb, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model: ${e.message}", e)
            throw RuntimeException("Could not init EmotionClassifier", e)
        }
    }

    /**
     * Classify a face bitmap into (label, confidence).
     * @return best label + probability in [0,1] (clamped).
     */
    fun classify(faceBitmap: Bitmap): Pair<String, Float> {
        // 1) Resize to model input
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)

        // 2) Prepare input buffer: float32 [1, 48, 48, 1]
        val inputBuffer = ByteBuffer
            .allocateDirect(4 * inputSize * inputSize) // 4 bytes per float
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val gray = (0.299f * r + 0.587f * g + 0.114f * b)

            val norm = when (NORMALIZATION_MODE) {
                NORMALIZE_MINUS_ONE_ONE -> (gray / 255f) * 2f - 1f   // [-1,1]
                else -> gray / 255f                                   // [0,1]
            }

            inputBuffer.putFloat(norm)
        }
        inputBuffer.rewind()

        // 3) Output buffer
        val output = Array(1) { FloatArray(labels.size) }

        try {
            interpreter.run(inputBuffer, output)
        } catch (e: Exception) {
            Log.e(TAG, "Interpreter.run failed: ${e.message}", e)
            // signal failure; caller can treat as "no prediction"
            return "neutral" to 0f
        }

        val probs = output[0]

        // --- DEBUG LOG: see raw probabilities to verify label order ---
        Log.d(
            TAG,
            "probs=" + probs.joinToString(prefix = "[", postfix = "]") {
                String.format("%.2f", it)
            }
        )

        // 4) Argmax
        var maxIdx = 0
        var maxVal = probs[0]
        for (i in 1 until probs.size) {
            if (probs[i] > maxVal) {
                maxVal = probs[i]
                maxIdx = i
            }
        }

        val label = labels.getOrElse(maxIdx) { "neutral" }
        val conf = maxVal.coerceIn(0f, 1f)

        Log.d(TAG, "predicted = $label (conf=${String.format("%.2f", conf)})")

        return label to conf
    }

    fun close() = interpreter.close()

    // ───────────────── helpers ─────────────────

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, assetName: String): ByteBuffer {
        context.assets.open(assetName).use { input ->
            val bytes = input.readBytes()
            return ByteBuffer
                .allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(bytes)
                    rewind()
                }
        }
    }
}
