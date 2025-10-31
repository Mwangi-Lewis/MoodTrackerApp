package com.example.moodtrackerapp

import android.content.Context
import android.graphics.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmotionClassifier (
    context: Context,
    modelAssetName: String = "emotion.tflite",
    private val labels: List<String> = listOf("angry","disgust","fear","happy","sad","surprise","neutral"),
    private val inputSize: Int = 48 // width & height
){
    private val interpreter: Interpreter

    init {
        val asset = context.assets.open(modelAssetName).readBytes()
        val bb = ByteBuffer.allocateDirect(asset.size).order(ByteOrder.nativeOrder())
        bb.put(asset)
        interpreter = Interpreter(bb)
    }

    fun classify(faceBitmap: Bitmap): Pair<String, Float> {
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        // grayscale [1, h, w, 1] float32 0..1
        val input = Array(1) { Array(inputSize) { FloatArray(inputSize) } }
        val output = Array(1) { FloatArray(labels.size) }

        val gray = IntArray(inputSize * inputSize)
        resized.getPixels(gray, 0, inputSize, 0, 0, inputSize, inputSize)
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val c = gray[y*inputSize + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val luma = (0.299f*r + 0.587f*g + 0.114f*b)/255f
                input[0][y][x] = luma
            }
        }

        interpreter.run(input, output)
        val probs = output[0]
        var maxIdx = 0
        var maxVal = -1f
        for (i in probs.indices) if (probs[i] > maxVal) { maxVal = probs[i]; maxIdx = i }
        return labels[maxIdx] to maxVal
    }

    fun close() = interpreter.close()
}