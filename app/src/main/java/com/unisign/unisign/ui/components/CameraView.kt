package com.unisign.unisign.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.unisign.unisign.logic.SignDetectionPipeline
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun LiveCameraView(
    modifier: Modifier = Modifier,
    pipeline: SignDetectionPipeline? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val cameraSelector = remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember { PreviewView(context) }
    val previewState = remember { mutableStateOf<Preview?>(null) }
    val cameraBound = remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // 0 = off, 1 = draw raw skeleton, 2 = draw normalized
    val drawMode = remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission.value = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission.value) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(hasCameraPermission.value, cameraSelector.value) {
        if (!hasCameraPermission.value) return@LaunchedEffect

        try {
            val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = 
                ProcessCameraProvider.getInstance(context)
            
            cameraProviderFuture.addListener(
                {
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider.value = provider
                        
                        provider.unbindAll()
                        
                        val preview = Preview.Builder()
                            .build()
                            .also { preview ->
                                // Defer setting the SurfaceProvider until the AndroidView has attached the PreviewView
                                previewState.value = preview
                            }

                        val isFront = cameraSelector.value == CameraSelector.DEFAULT_FRONT_CAMERA

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    pipeline?.let { p ->
                                        val bitmap = imageProxyToBitmap(imageProxy)
                                        if (bitmap != null) {
                                            p.processFrame(bitmap, isFront)
                                        }
                                    }
                                    imageProxy.close()
                                }
                            }

                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector.value,
                            preview,
                            imageAnalysis
                        )
                        
                        cameraBound.value = true
                        Log.d("LiveCameraView", "Camera bound successfully with selector: ${cameraSelector.value}")
                    } catch (exc: Exception) {
                        Log.e("LiveCameraView", "Error binding camera", exc)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        } catch (exc: Exception) {
            Log.e("LiveCameraView", "Error getting camera provider", exc)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission.value) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewView },
                update = { view ->
                    view.scaleType = PreviewView.ScaleType.FILL_CENTER
                    // If preview has been created and camera bound, set the surface provider now to avoid
                    // timing issues where PreviewView isn't attached when Preview.setSurfaceProvider is called.
                    previewState.value?.let { preview ->
                        try {
                            preview.setSurfaceProvider(view.surfaceProvider)
                        } catch (e: Exception) {
                            Log.e("LiveCameraView", "Failed to set surface provider on preview", e)
                        }
                    }
                }
            )

            // Overlay canvas for drawing skeleton / normalized points
            val rawLandmarksState = pipeline?.latestRawLandmarks?.collectAsState(initial = null)
            val normalizedState = pipeline?.latestNormalized?.collectAsState(initial = null)
            val modelStatusState = pipeline?.modelLoadStatus?.collectAsState(initial = "unknown")
            val modelHeaderState = pipeline?.modelHeaderInfo?.collectAsState(initial = null)
            val modelShaState = pipeline?.modelSha256?.collectAsState(initial = null)
            val modelSizeState = pipeline?.modelFileSize?.collectAsState(initial = null)
            val lastExtractionState = pipeline?.lastExtractionInfo?.collectAsState(initial = "no_frames")
            val predictionState = pipeline?.predictionState?.collectAsState(initial = SignDetectionPipeline.PredictionState.Idle)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw raw MediaPipe landmarks projected to view size
                if (drawMode.value == 1) {
                    val raw = rawLandmarksState?.value
                    raw?.let { data ->
                        // helper to draw points from a flat array of normalized coords (x,y)
                        fun drawArray(arr: FloatArray?, color: androidx.compose.ui.graphics.Color, connections: List<Pair<Int, Int>>? = null, offsetIndex: Int = 0) {
                            arr?.let { a ->
                                                val pts = List(a.size / 2) { i -> Offset(a[i * 2] * w, a[i * 2 + 1] * h) }
                                // lines
                                connections?.forEach { (i, j) ->
                                    if (i in pts.indices && j in pts.indices) {
                                        drawLine(color = color, start = pts[i], end = pts[j], strokeWidth = 3f)
                                    }
                                }
                                // points
                                pts.forEach { p -> drawCircle(color = color, radius = 6f, center = p) }
                            }
                        }

                        // Hand finger connections (wrist is index 0 in hand arrays)
                        val handConnections = listOf(
                            // thumb
                            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
                            // index
                            listOf(0 to 5, 5 to 6, 6 to 7, 7 to 8),
                            // middle
                            listOf(0 to 9, 9 to 10, 10 to 11, 11 to 12),
                            // ring
                            listOf(0 to 13, 13 to 14, 14 to 15, 15 to 16),
                            // pinky
                            listOf(0 to 17, 17 to 18, 18 to 19, 19 to 20)
                        ).flatten()

                        // draw left hand (if present)
                        drawArray(data.leftHand, Color.Cyan, handConnections)
                        // draw right hand
                        drawArray(data.rightHand, Color.Magenta, handConnections)

                        // draw face keypoints (use the same indices as LandmarkNormalizer's faceIndices)
                        data.face?.let { faceArr ->
                                    val faceIndices = listOf(0, 1, 4, 10, 33, 61, 133, 152, 263, 291, 362, 13, 14)
                                    val pts = faceIndices.mapNotNull { idx ->
                                        if (idx * 2 + 1 < faceArr.size) Offset(faceArr[idx * 2] * w, faceArr[idx * 2 + 1] * h) else null
                                    }
                            for (i in 0 until pts.size - 1) drawLine(color = Color.Yellow, start = pts[i], end = pts[i + 1], strokeWidth = 2f)
                            pts.forEach { p -> drawCircle(color = Color.Yellow, radius = 3f, center = p) }
                        }

                        // draw selected pose points
                        data.pose?.let { pArr ->
                            val poseIndices = intArrayOf(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
                            val poseIndicesList = listOf(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
                            val pts = poseIndicesList.mapNotNull { idx ->
                                if (idx * 2 + 1 < pArr.size) Offset(pArr[idx * 2] * w, pArr[idx * 2 + 1] * h) else null
                            }
                            for (i in 0 until pts.size - 1) drawLine(color = Color.Green, start = pts[i], end = pts[i + 1], strokeWidth = 3f)
                            pts.forEach { p -> drawCircle(color = Color.Green, radius = 5f, center = p) }
                        }
                    }
                }

                // Draw normalized points (centered) when mode == 2
                if (drawMode.value == 2) {
                    val norm = normalizedState?.value
                    norm?.let { arr ->
                        // arr contains 69*2 points ordered the same as the training input
                        val cx = w / 2f
                        val cy = h / 2f
                        val scale = minOf(w, h) / 3f
                        val pts = List(arr.size / 2) { i -> Offset(cx + arr[i * 2] * scale, cy + arr[i * 2 + 1] * scale) }

                        // draw a few logical connections: hands/fingers similar to above
                        fun drawHandBase(startIndex: Int, color: androidx.compose.ui.graphics.Color) {
                            val base = startIndex
                            val handConnections = listOf(
                                0 to 1, 1 to 2, 2 to 3, 3 to 4,
                                0 to 5, 5 to 6, 6 to 7, 7 to 8,
                                0 to 9, 9 to 10, 10 to 11, 11 to 12,
                                0 to 13, 13 to 14, 14 to 15, 15 to 16,
                                0 to 17, 17 to 18, 18 to 19, 19 to 20
                            )
                            handConnections.forEach { (i, j) ->
                                val ii = base + i
                                val jj = base + j
                                if (ii in pts.indices && jj in pts.indices) drawLine(color = color, start = pts[ii], end = pts[jj], strokeWidth = 2f)
                            }
                        }

                        // left hand indices 0..20
                        drawHandBase(0, Color.Cyan)
                        // right hand 21..41
                        drawHandBase(21, Color.Magenta)

                        // face 42..54 (13 points) draw sequentially
                        val faceStart = 42
                        for (i in faceStart until faceStart + 13 - 1) {
                            if (i in pts.indices && i + 1 in pts.indices) drawLine(color = Color.Yellow, start = pts[i], end = pts[i + 1], strokeWidth = 1.5f)
                        }

                        // pose 55..68
                        val poseStart = 55
                        for (i in poseStart until poseStart + 14 - 1) {
                            if (i in pts.indices && i + 1 in pts.indices) drawLine(color = Color.Green, start = pts[i], end = pts[i + 1], strokeWidth = 2f)
                        }

                        pts.forEach { p -> drawCircle(color = Color.White, radius = 3f, center = p) }
                    }
                }
            }
            // Show the last camera frame (debug) in top-right corner so we can verify what MediaPipe sees
            val lastFrameState = pipeline?.lastFrameBitmap?.collectAsState(initial = null)
            lastFrameState?.value?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Last frame (debug)",
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }

            // Small debug text overlay showing model & pipeline state
            val statusText = buildString {
                append("Model: ")
                append(modelStatusState?.value ?: "n/a")
                append(" ")
                modelSizeState?.value?.let { append("size=${it}") }
                append(" ")
                modelShaState?.value?.let { append("sha=${it.substring(0,12)}...") }
                append("\n")
                append("Landmarks: ")
                val raw = rawLandmarksState?.value
                if (raw == null) append("none") else {
                    val left = raw.leftHand?.size?.div(2) ?: 0
                    val right = raw.rightHand?.size?.div(2) ?: 0
                    val face = raw.face?.size?.div(2) ?: 0
                    val pose = raw.pose.size / 2
                    append("pose=$pose face=$face left=$left right=$right")
                }
                append("\n")
                append("Prediction: ")
                append(when (val s = predictionState?.value) {
                    is SignDetectionPipeline.PredictionState.Result -> "Result(${s.predictions.size})"
                    is SignDetectionPipeline.PredictionState.Detecting -> "Detecting"
                    is SignDetectionPipeline.PredictionState.Idle -> "Idle"
                    else -> "n/a"
                })
                append("\n")
                append("LastExtract: ")
                append(lastExtractionState?.value ?: "n/a")
                modelHeaderState?.value?.let { hdr ->
                    append("\n\nHeader:\n")
                    append(hdr)
                }
            }

            // Text drawn as Compose overlay (top-center)
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.material3.Text(
                    text = statusText,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                )
            }
            IconButton(
                onClick = {
                    cameraSelector.value = if (cameraSelector.value == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch Camera",
                    tint = Color.White
                )
            }

            // Debug toggle for drawing skeleton / normalized points
            Button(
                onClick = {
                    drawMode.value = (drawMode.value + 1) % 3 // cycles 0->1->2->0
                },
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                val label = when (drawMode.value) {
                    0 -> "Overlay: Off"
                    1 -> "Overlay: Skeleton"
                    2 -> "Overlay: Normalized"
                    else -> "Overlay"
                }
                Text(text = label, color = Color.White, fontSize = 12.sp)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Camera permission required",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/**
 * Convert ImageProxy (YUV_420_888) to Bitmap for MediaPipe processing.
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        // Log basic info for debugging
        Log.d("LiveCameraView", "imageProxyToBitmap: w=${imageProxy.width} h=${imageProxy.height} format=${imageProxy.format} rotation=${imageProxy.imageInfo.rotationDegrees}")

        // Convert YUV_420_888 to NV21 properly by handling rowStride and pixelStride
        val width = imageProxy.width
        val height = imageProxy.height
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val nv21 = ByteArray(width * height + 2 * (width / 2) * (height / 2))

        // Fill Y using a safe copy of the plane buffer (avoid BufferUnderflow by reading once)
        var pos = 0
        val yBuffer = yPlane.buffer
        val yBytes = ByteArray(yBuffer.remaining())
        yBuffer.get(yBytes)
        Log.d("LiveCameraView", "Y plane: rowStride=$yRowStride bytes=${yBytes.size}")

        for (row in 0 until height) {
            val srcPos = row * yRowStride
            val copyLen = if (srcPos + width <= yBytes.size) width else maxOf(0, yBytes.size - srcPos)
            if (copyLen > 0) {
                System.arraycopy(yBytes, srcPos, nv21, pos, copyLen)
                if (copyLen < width) {
                    // pad remaining with zeros
                    for (k in 0 until (width - copyLen)) nv21[pos + copyLen + k] = 0
                }
            } else {
                // nothing to copy, fill zeros
                for (k in 0 until width) nv21[pos + k] = 0
            }
            pos += width
        }

        // Fill interleaved VU (NV21) from U and V planes safely
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uBytes = ByteArray(uBuffer.remaining())
        val vBytes = ByteArray(vBuffer.remaining())
        uBuffer.get(uBytes)
        vBuffer.get(vBytes)
        Log.d("LiveCameraView", "U plane: rowStride=$uvRowStride pixelStride=$uvPixelStride bytes=${uBytes.size}")

        val chromaHeight = (height + 1) / 2
        val chromaWidth = (width + 1) / 2
        for (row in 0 until chromaHeight) {
            val uRowStart = row * uvRowStride
            val vRowStart = row * uvRowStride
            for (col in 0 until chromaWidth) {
                val uIndex = uRowStart + col * uvPixelStride
                val vIndex = vRowStart + col * uvPixelStride
                val uVal = if (uIndex in uBytes.indices) uBytes[uIndex] else 0
                val vVal = if (vIndex in vBytes.indices) vBytes[vIndex] else 0
                // NV21 expects V then U
                nv21[pos++] = vVal
                nv21[pos++] = uVal
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        try {
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        } catch (e: Exception) {
            Log.e("LiveCameraView", "compressToJpeg failed", e)
            throw e
        }
        val bytes = out.toByteArray()
        Log.d("LiveCameraView", "YUV->JPEG bytes=${bytes.size}")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Rotate according to image rotation and return the rotated bitmap
        val rotation = imageProxy.imageInfo.rotationDegrees
        val resultBitmap = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
        Log.d("LiveCameraView", "bitmapCreated: ${resultBitmap.width}x${resultBitmap.height}")
        return resultBitmap
    } catch (e: Exception) {
        Log.e("LiveCameraView", "Error converting ImageProxy to Bitmap", e)
        null
    }
}
