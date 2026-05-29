package com.unisign.unisign.logic

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Orchestrates the sign detection pipeline:
 * Camera frame → MediaPipe skeleton → Normalize → Buffer → ExecuTorch inference → Prediction
 */
class SignDetectionPipeline(private val context: Context) {
    companion object {
        private const val TAG = "SignDetectionPipeline"
        private const val WINDOW_SIZE = 90
        private const val STRIDE = 15
        private const val MAX_LEN = 150
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val MODEL_ASSET_NAME = "model.pte"
        private const val EXECUTORCH_HEADER_MIN_SIZE = 24L
    }

    private var skeletonExtractor: SkeletonExtractor? = null
    private var module: Module? = null
    private val frameBuffer = ArrayList<FloatArray>(WINDOW_SIZE)
    private var frameCount = 0

    private val _predictionState = MutableStateFlow<PredictionState>(PredictionState.Idle)
    val predictionState: StateFlow<PredictionState> = _predictionState

    // Publish the most-recent raw landmark sets and the most-recent normalized frame so the UI
    // can draw overlays (skeleton / normalized) for debugging.
    data class LandmarkDisplayData(
        val pose: FloatArray,
        val face: FloatArray?,
        val leftHand: FloatArray?,
        val rightHand: FloatArray?
    )

    private val _latestRawLandmarks = MutableStateFlow<LandmarkDisplayData?>(null)
    val latestRawLandmarks: StateFlow<LandmarkDisplayData?> = _latestRawLandmarks

    private val _latestNormalized = MutableStateFlow<FloatArray?>(null)
    val latestNormalized: StateFlow<FloatArray?> = _latestNormalized

    // Expose model load status for UI/debugging
    private val _modelLoadStatus = MutableStateFlow("not_initialized")
    val modelLoadStatus: StateFlow<String> = _modelLoadStatus
    // If model invalid, expose header/debug info for inspection
    private val _modelHeaderInfo = MutableStateFlow<String?>(null)
    val modelHeaderInfo: StateFlow<String?> = _modelHeaderInfo
    // Expose runtime-copied model checksum and size for verification
    private val _modelSha256 = MutableStateFlow<String?>(null)
    val modelSha256: StateFlow<String?> = _modelSha256
    private val _modelFileSize = MutableStateFlow<Long?>(null)
    val modelFileSize: StateFlow<Long?> = _modelFileSize

    // Info about last extraction attempt (used to debug when no skeleton is detected)
    private val _lastExtractionInfo = MutableStateFlow("no_frames")
    val lastExtractionInfo: StateFlow<String> = _lastExtractionInfo

    // Publish the last camera frame (small bitmap) for debug overlay so we can verify
    // what MediaPipe actually sees.
    private val _lastFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val lastFrameBitmap: StateFlow<Bitmap?> = _lastFrameBitmap

    data class Prediction(
        val classIdx: Int,
        val gloss: String,
        val hebrew: String,
        val confidence: Float
    )

    sealed class PredictionState {
        object Idle : PredictionState()
        object Detecting : PredictionState()
        data class Result(val predictions: List<Prediction>) : PredictionState()
    }

    fun initialize() {
        try {
            GlossMapper.init(context)
            skeletonExtractor = SkeletonExtractor(context)

            _modelLoadStatus.value = "copying_asset"
            val modelFile = copyModelAssetToInternalStorage()
            _modelLoadStatus.value = "copied"
            // compute checksum & size of the copied file for debugging
            try {
                val f = File(modelFile)
                _modelFileSize.value = f.length()
                // compute sha256
                java.io.FileInputStream(f).use { fis ->
                    val buffer = ByteArray(8192)
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    var read: Int
                    while (fis.read(buffer).also { read = it } > 0) {
                        md.update(buffer, 0, read)
                    }
                    val digest = md.digest()
                    val hex = digest.joinToString("") { b -> String.format("%02x", b) }
                    _modelSha256.value = hex
                }
            } catch (_: Exception) {
            }
            if (!isLikelyValidExecuTorchModel(modelFile)) {
                Log.e(
                    TAG,
                    "Skipping ExecuTorch load because $MODEL_ASSET_NAME does not look like a valid model file: $modelFile"
                )
                module = null
                _predictionState.value = PredictionState.Idle
                _modelLoadStatus.value = "invalid_model"
                _modelHeaderInfo.value = getModelHeaderDebug(modelFile)
                return
            }

            _modelLoadStatus.value = "loading"
            module = runCatching { Module.load(modelFile) }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load ExecuTorch module from $modelFile", error)
                    _modelLoadStatus.value = "failed: ${error.message}"
                }
                .getOrNull()

            if (module == null) {
                _predictionState.value = PredictionState.Idle
                if (!_modelLoadStatus.value.startsWith("failed")) {
                    _modelLoadStatus.value = "not_loaded"
                }
                return
            }

            _predictionState.value = PredictionState.Detecting
            _modelLoadStatus.value = "loaded"
            // clear header debug on success
            _modelHeaderInfo.value = null
            Log.d(TAG, "Pipeline initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize pipeline", e)
            _modelLoadStatus.value = "failed: ${e.message}"
        }
    }

    private fun getModelHeaderDebug(modelPath: String): String {
        return try {
            val file = File(modelPath)
            val sb = StringBuilder()
            sb.append("path=${file.absolutePath}\n")
            sb.append("size=${file.length()}\n")
            FileInputStream(file).use { input ->
                val header = ByteArray(minOf(64L, file.length()).toInt())
                val read = input.read(header)
                sb.append("read_bytes=$read\n")
                sb.append("header_hex=")
                for (i in 0 until read) {
                    sb.append(String.format("%02X", header[i]))
                    if (i % 16 == 15) sb.append(" ")
                }
                sb.append("\n")
                if (read >= 24) {
                    val magic = String(header, 4, 4, Charsets.US_ASCII)
                    val format = String(header, 8, 4, Charsets.US_ASCII)
                    val declaredSize = ByteBuffer.wrap(header, 16, 8).order(ByteOrder.LITTLE_ENDIAN).long
                    sb.append("magic=$magic format=$format declaredSize=$declaredSize\n")
                }
            }
            sb.toString()
        } catch (e: Exception) {
            "header_read_error: ${e.message}"
        }
    }

    /**
     * Process a single camera frame through the full pipeline.
     * Call this from the ImageAnalysis analyzer on a background thread.
     */
    fun processFrame(bitmap: Bitmap, isFrontCamera: Boolean) {
        val extractor = skeletonExtractor ?: return
        val mod = module ?: return
        Log.d(TAG, "processFrame called; frameCount=$frameCount")

        // Publish the last frame for UI debugging (small copy to avoid accidental mutation)
        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 320, (bitmap.height * 320f / bitmap.width).toInt(), true)
            _lastFrameBitmap.value = scaled
            Log.d(TAG, "Published lastFrameBitmap ${scaled.width}x${scaled.height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish lastFrameBitmap: ${e.message}", e)
        }

        // Step 1: Extract landmarks via MediaPipe
        val landmarks = extractor.extract(bitmap, isFrontCamera)
        if (landmarks == null) {
            // No pose detected in frame - still accumulating
            Log.d(TAG, "No landmarks extracted for this frame")
            _lastExtractionInfo.value = "no_pose"
            // clear any previous normalized/latest raw so overlay shows none
            _latestRawLandmarks.value = null
            _latestNormalized.value = null
            return
        }

        // Publish raw landmarks for UI overlay
        _latestRawLandmarks.value = LandmarkDisplayData(
            pose = landmarks.pose,
            face = landmarks.face,
            leftHand = landmarks.leftHand,
            rightHand = landmarks.rightHand
        )
        // publish extraction counts
        val poseCount = landmarks.pose.size / 2
        val faceCount = landmarks.face?.size?.div(2) ?: 0
        val leftCount = landmarks.leftHand?.size?.div(2) ?: 0
        val rightCount = landmarks.rightHand?.size?.div(2) ?: 0
        _lastExtractionInfo.value = "pose=$poseCount face=$faceCount left=$leftCount right=$rightCount"

        // Step 2: Normalize landmarks
        val normalized = LandmarkNormalizer.normalizeFrame(
            landmarks.pose, landmarks.face, landmarks.leftHand, landmarks.rightHand
        ) ?: return

        // Publish normalized coordinates for UI overlay (debug mode)
        _latestNormalized.value = normalized

        // Step 3: Add to buffer
        synchronized(frameBuffer) {
            frameBuffer.add(normalized)
            if (frameBuffer.size > WINDOW_SIZE) {
                frameBuffer.removeAt(0)
            }
        }

        frameCount++

        // Step 4: Run inference every STRIDE frames
        if (frameCount % STRIDE == 0 && frameBuffer.size >= 5) {
            runInference(mod)
        }
    }

    private fun runInference(mod: Module) {
        val frames: List<FloatArray>
        synchronized(frameBuffer) {
            frames = ArrayList(frameBuffer)
        }

        val numFrames = frames.size

        // Pad to MAX_LEN: create (1, MAX_LEN, 69, 2) tensor
        val tensorData = FloatArray(MAX_LEN * 69 * 2)
        for (t in 0 until minOf(numFrames, MAX_LEN)) {
            val frame = frames[t]
            System.arraycopy(frame, 0, tensorData, t * 69 * 2, 69 * 2)
        }
        // Remaining frames stay 0.0 (zero-padded)

        val inputTensor = Tensor.fromBlob(tensorData, longArrayOf(1, MAX_LEN.toLong(), 69, 2))
        try {
            val result = mod.forward(EValue.from(inputTensor))
            val outputTensor = result[0].toTensor()
            val logits = outputTensor.dataAsFloatArray

            // Softmax
            val probs = softmax(logits)

            // Top-3 predictions
            val indexed = probs.mapIndexed { idx, prob -> idx to prob }
                .sortedByDescending { it.second }
                .take(3)

            val predictions = indexed
                .filter { it.second >= CONFIDENCE_THRESHOLD }
                .map { (idx, conf) ->
                    Prediction(
                        classIdx = idx,
                        gloss = GlossMapper.idxToGloss(idx),
                        hebrew = GlossMapper.idxToHebrew(idx),
                        confidence = conf
                    )
                }

            _predictionState.value = if (predictions.isNotEmpty()) {
                PredictionState.Result(predictions)
            } else {
                PredictionState.Detecting
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            // Attempt to read log buffer if available in the library version
            try {
                val logBuffer = mod.javaClass.getMethod("readLogBuffer").invoke(mod) as String
                if (logBuffer.isNotEmpty()) {
                    Log.e(TAG, "ExecuTorch Log Buffer: $logBuffer")
                }
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = FloatArray(logits.size) { kotlin.math.exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return FloatArray(exps.size) { exps[it] / sum }
    }

    @Suppress("unused")
    fun reset() {
        synchronized(frameBuffer) {
            frameBuffer.clear()
        }
        frameCount = 0
        _predictionState.value = PredictionState.Detecting
    }

    fun release() {
        skeletonExtractor?.close()
        skeletonExtractor = null
        module = null
        frameBuffer.clear()
    }

    /**
     * Copy asset to internal storage and return the file path.
     * ExecuTorch Module.load() requires a file path, not an asset stream.
     */
    private fun copyModelAssetToInternalStorage(): String {
        val file = File(context.filesDir, MODEL_ASSET_NAME)

        // Refresh the cached model on every launch so stale/corrupted copies never survive.
        val tempFile = File(context.filesDir, "$MODEL_ASSET_NAME.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        if (file.exists()) {
            file.delete()
        }

        context.assets.open(MODEL_ASSET_NAME).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
        return file.absolutePath
    }

    private fun isLikelyValidExecuTorchModel(modelPath: String): Boolean {
        val file = File(modelPath)
        if (!file.exists() || file.length() < EXECUTORCH_HEADER_MIN_SIZE) {
            Log.e(TAG, "ExecuTorch model is missing or too small: ${file.absolutePath}")
            return false
        }

        FileInputStream(file).use { input ->
            val header = ByteArray(EXECUTORCH_HEADER_MIN_SIZE.toInt())
            val read = input.read(header)
            if (read < EXECUTORCH_HEADER_MIN_SIZE) {
                Log.e(TAG, "Could not read the full ExecuTorch header: ${file.absolutePath}")
                return false
            }

            val magic = String(header, 4, 4, Charsets.US_ASCII)
            val format = String(header, 8, 4, Charsets.US_ASCII)
            if (magic != "ET12" || format != "eh00") {
                Log.e(TAG, "Unexpected ExecuTorch header magic: magic=$magic format=$format")
                return false
            }

            val declaredSize = ByteBuffer.wrap(header, 16, 8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .long

            if (declaredSize <= 0L || declaredSize > file.length()) {
                Log.e(
                    TAG,
                    "ExecuTorch header size is invalid: declared=$declaredSize actual=${file.length()}"
                )
                return false
            }
        }

        return true
    }
}
