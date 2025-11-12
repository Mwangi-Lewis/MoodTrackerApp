@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.moodtrackerapp

import android.Manifest
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
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
import com.google.mlkit.vision.face.*
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

private const val TAG = "InlineSelfie"

// ───────────────────────── Public composable ─────────────────────────

@Composable
fun InlineSelfieCamera(
    modifier: Modifier = Modifier,
    onResult: (mood: String, conf: Float) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // Request camera permission once
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // if denied, just close for now
            onClose()
        }
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Emotion classifier
    val classifier = remember { EmotionClassifier(context) }
    DisposableEffect(Unit) { onDispose { classifier.close() } }

    // ML Kit face detector
    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }
    DisposableEffect(Unit) { onDispose { faceDetector.close() } }

    var status by remember { mutableStateOf("Looking for your face…") }
    var latestMood by remember { mutableStateOf<String?>(null) }
    var latestConf by remember { mutableStateOf<Float?>(null) }
    var hasFace by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp) {
        Column(
            modifier
                .padding(12.dp)
        ) {
            Text("Selfie Camera", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            CameraPreviewInline(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                faceDetector = faceDetector,
                classifier = classifier,
                onFaceEmotion = { mood, conf ->
                    // Called only when a face AND prediction exist
                    hasFace = true
                    latestMood = mood
                    latestConf = conf
                    status = "Detected: $mood (${(conf * 100).toInt()}%)"
                },
                onNoFace = {
                    hasFace = false
                    latestMood = null
                    latestConf = null
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
                    onClick = {
                        onResult(latestMood ?: "neutral", latestConf ?: 0f)
                    }
                ) {
                    Text("Use Result")
                }
            }
        }
    }
}

// ─────────────────────── Camera / Analyzer view ───────────────────────

@androidx.annotation.OptIn(ExperimentalGetImage::class)
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

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                try {
                    cameraProvider.unbindAll()

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()

                    val converter = YuvToRgbConverter(ctx)

                    var lastSuccessTs = 0L

                    analysis.setAnalyzer(executor) { proxy ->
                        val media = proxy.image
                        if (media == null) {
                            proxy.close()
                            return@setAnalyzer
                        }

                        val rotation = proxy.imageInfo.rotationDegrees
                        val image = InputImage.fromMediaImage(media, rotation)

                        faceDetector.process(image)
                            .addOnSuccessListener { faces ->
                                if (faces.isNotEmpty()) {
                                    // basic throttle: only classify every ~400ms
                                    val now = System.currentTimeMillis()
                                    if (now - lastSuccessTs < 400L) {
                                        // still considered "face found"; don't spam onNoFace
                                        return@addOnSuccessListener
                                    }
                                    lastSuccessTs = now

                                    val face = faces.first()
                                    try {
                                        val frameBitmap =
                                            proxy.toBitmap(converter).rotate(rotation)
                                        val faceBitmap =
                                            cropFace(frameBitmap, face.boundingBox)

                                        val (label, conf) = classifier.classify(faceBitmap)
                                        Log.d(TAG, "Face ok -> $label ($conf)")
                                        onFaceEmotion(label, conf)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Crop/classify failed: ${e.message}", e)
                                        onNoFace()
                                    }
                                } else {
                                    onNoFace()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Detector error: ${e.message}", e)
                                onNoFace()
                            }
                            .addOnCompleteListener {
                                proxy.close()
                            }
                    }

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "bindToLifecycle failed: ${e.message}", e)
                }
            }, executor)

            previewView
        }
    )
}

// ─────────────────────── Helpers: YUV → Bitmap, crop ───────────────────────

private class YuvToRgbConverter(private val context: Context) {
    fun yuvToRgb(image: ImageProxy, out: Bitmap) {
        val nv21 = yuv420888ToNv21(image)
        @Suppress("DEPRECATION")
        val yuvImage = android.graphics.YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        val outStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height),
            100,
            outStream
        )
        val jpeg = outStream.toByteArray()
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val canvas = Canvas(out)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        bmp.recycle()
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
        val chromaRowPadding = chromaRowStride - image.width / 2

        var offset = ySize
        if (chromaRowPadding == 0) {
            vBuffer.get(nv21, offset, vSize)
            offset += vSize
            uBuffer.get(nv21, offset, uSize)
        } else {
            for (row in 0 until image.height / 2) {
                vBuffer.get(nv21, offset, image.width / 2)
                offset += image.width / 2
                if (row < image.height / 2 - 1) {
                    vBuffer.position(vBuffer.position() + chromaRowPadding)
                }
            }
            for (row in 0 until image.height / 2) {
                uBuffer.get(nv21, offset, image.width / 2)
                offset += image.width / 2
                if (row < image.height / 2 - 1) {
                    uBuffer.position(uBuffer.position() + chromaRowPadding)
                }
            }
        }
        return nv21
    }
}

private fun ImageProxy.toBitmap(converter: YuvToRgbConverter): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    converter.yuvToRgb(this, bmp)
    return bmp
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated != this) recycle()
    return rotated
}

private fun cropFace(source: Bitmap, box: Rect): Bitmap {
    val left = max(0, box.left)
    val top = max(0, box.top)
    val right = min(source.width, box.right)
    val bottom = min(source.height, box.bottom)

    val w = max(1, right - left)
    val h = max(1, bottom - top)

    return Bitmap.createBitmap(source, left, top, w, h)
}
