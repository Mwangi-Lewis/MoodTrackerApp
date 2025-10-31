@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.moodtrackerapp

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Small inline camera composable to capture and classify mood directly from the Home screen.
 * Uses CameraX + ML Kit Face Detection + TensorFlow Lite (EmotionClassifier).
 */
@Composable
fun InlineSelfieCamera(
    modifier: Modifier = Modifier,
    onResult: (mood: String, conf: Float) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optionally show message if denied */ }
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }

    // TFLite classifier (must implement classify(bitmap): Pair<String, Float>)
    val classifier = remember { EmotionClassifier(context) }
    DisposableEffect(Unit) { onDispose { classifier.close() } }

    // Friendlier/forgiving FaceDetector
    val faceDetector: FaceDetector = remember {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f) // 10% of image height
            .build()
        FaceDetection.getClient(opts)
    }
    DisposableEffect(Unit) { onDispose { faceDetector.close() } }

    var latestMood by remember { mutableStateOf<String?>(null) }
    var latestConf by remember { mutableStateOf<Float?>(null) }
    var hasFace by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Looking for your face…") }

    Surface(tonalElevation = 2.dp) {
        Column(modifier.padding(12.dp)) {
            Text("Selfie Camera", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            CameraPreviewInline(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                faceDetector = faceDetector,
                classifier = classifier,
                onFaceEmotion = { mood, conf ->
                    latestMood = mood
                    latestConf = conf
                    hasFace = true
                    status = "Detected: $mood (${((conf) * 100).toInt()}%)"
                },
                onNoFace = {
                    hasFace = false
                    status = "Looking for your face…"
                }
            )

            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClose) { Text("Cancel") }
                Button(
                    enabled = hasFace && latestMood != null && latestConf != null,
                    onClick = { onResult(latestMood ?: "neutral", latestConf ?: 0f) }
                ) { Text("Use Result") }
            }
        }
    }
}

@Composable
private fun CameraPreviewInline(
    modifier: Modifier,
    faceDetector: FaceDetector,
    classifier: EmotionClassifier,
    onFaceEmotion: (mood: String, conf: Float) -> Unit,
    onNoFace: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            providerFuture.addListener({
                val provider = providerFuture.get()
                try {
                    provider.unbindAll()

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // Force YUV so proxy.image is available reliably
                    val analysis = ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val converter = YuvToRgbConverter(ctx)

                    var lastTs = 0L
                    val MIN_GAP_MS = 250L

                    analysis.setAnalyzer(executor) { proxy ->
                        val now = System.currentTimeMillis()
                        if (now - lastTs < MIN_GAP_MS) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        lastTs = now

                        // Safely get MediaImage
                        val media = proxy.image ?: run {
                            Log.d("SelfieCam", "proxy.image == null")
                            proxy.close()
                            return@setAnalyzer
                        }
                        val rotation = proxy.imageInfo.rotationDegrees
                        val input = InputImage.fromMediaImage(media, rotation)

                        // One ML Kit call; close proxy only when finished
                        faceDetector.process(input)
                            .addOnSuccessListener { faces ->
                                if (faces.isNotEmpty()) {
                                    Log.d("SelfieCam", "Faces detected: ${faces.size}")
                                    try {
                                        val bmp = proxy.toBitmap(converter).rotate(rotation)
                                        val (mood, conf) = classifier.classify(bmp)
                                        onFaceEmotion(mood, conf)
                                    } catch (e: Exception) {
                                        Log.w("SelfieCam", "Classify failed: ${e.message}")
                                        onNoFace()
                                    }
                                } else {
                                    Log.d("SelfieCam", "No face")
                                    onNoFace()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w("SelfieCam", "Detector error: ${e.message}")
                                onNoFace()
                            }
                            .addOnCompleteListener {
                                proxy.close()
                            }
                    }

                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                } catch (_: Exception) {
                    // ignore binding failures gracefully
                }
            }, executor)

            previewView
        }
    )
}

/* ---------- YUV → Bitmap conversion + rotation helpers ---------- */

private class YuvToRgbConverter(private val context: Context) {
    fun yuvToRgb(image: ImageProxy, out: Bitmap) {
        val nv21 = yuv420888ToNv21(image)
        @Suppress("DEPRECATION")
        val yuvImage = android.graphics.YuvImage(
            nv21, ImageFormat.NV21, image.width, image.height, null
        )
        val outStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, outStream)
        val jpeg = outStream.toByteArray()
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        android.graphics.Canvas(out).drawBitmap(decoded, 0f, 0f, null)
        decoded.recycle()
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)

        val chromaRowStride = image.planes[1].rowStride
        val rowPadding = chromaRowStride - image.width / 2
        var offset = ySize

        if (rowPadding == 0) {
            vBuffer.get(nv21, offset, vSize); offset += vSize
            uBuffer.get(nv21, offset, uSize)
        } else {
            for (row in 0 until image.height / 2) {
                vBuffer.get(nv21, offset, image.width / 2); offset += image.width / 2
                if (row < image.height / 2 - 1) vBuffer.position(vBuffer.position() + rowPadding)
            }
            for (row in 0 until image.height / 2) {
                uBuffer.get(nv21, offset, image.width / 2); offset += image.width / 2
                if (row < image.height / 2 - 1) uBuffer.position(uBuffer.position() + rowPadding)
            }
        }
        return nv21
    }
}

private fun ImageProxy.toBitmap(converter: YuvToRgbConverter): Bitmap {
    val bmp = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
    converter.yuvToRgb(this, bmp)
    return bmp
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    if (rotated !== this) this.recycle()
    return rotated
}