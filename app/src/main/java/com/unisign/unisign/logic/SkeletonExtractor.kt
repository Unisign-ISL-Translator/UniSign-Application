package com.unisign.unisign.logic

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Extracts 69-joint skeleton from camera frames using MediaPipe.
 * Joint order: left_hand[0:21], right_hand[21:42], face[42:55], pose[55:69]
 */
class SkeletonExtractor(context: Context) {
    companion object {
        private const val TAG = "SkeletonExtractor"
    }

    private val poseLandmarker: PoseLandmarker
    private val handLandmarker: HandLandmarker
    private val faceLandmarker: FaceLandmarker

    init {
        try {
            // Log available assets to help debug missing task files
            try {
                val assetList = context.assets.list("")?.joinToString(",") ?: "(none)"
                Log.d(TAG, "Assets root: $assetList")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to list assets: ${e.message}")
            }

            val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("pose_landmarker_lite.task")
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)
            Log.d(TAG, "PoseLandmarker initialized")

            val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("hand_landmarker.task")
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(2)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(context, handOptions)
            Log.d(TAG, "HandLandmarker initialized")

            val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task")
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .build()
            faceLandmarker = FaceLandmarker.createFromOptions(context, faceOptions)
            Log.d(TAG, "FaceLandmarker initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe landmarkers: ${e.message}", e)
            // Re-throw to let caller know initialization failed
            throw e
        }
    }

    /**
     * Extract landmarks from a bitmap frame.
     * @param bitmap The camera frame as a Bitmap
     * @param isFrontCamera Whether the frame is from front camera (for hand label mirroring)
     * @return Raw landmark arrays: (pose, face, leftHand, rightHand) or null if pose not detected
     */
    fun extract(bitmap: Bitmap, isFrontCamera: Boolean): LandmarkResult? {
        val mpImage = BitmapImageBuilder(bitmap).build()

        // Pose detection
        val poseResult: PoseLandmarkerResult = try {
            poseLandmarker.detect(mpImage)
        } catch (e: Exception) {
            Log.e(TAG, "PoseLandmarker.detect failed: ${e.message}", e)
            return null
        }
        if (poseResult.landmarks().isEmpty()) {
            Log.d(TAG, "PoseLandmarker returned no landmarks")
            return null
        }
        val poseLandmarks = poseResult.landmarks()[0]

        // Flatten pose to FloatArray (33 landmarks * 2 coords)
        val poseArray = FloatArray(33 * 2)
        for (i in 0 until minOf(33, poseLandmarks.size)) {
            poseArray[i * 2] = poseLandmarks[i].x()
            poseArray[i * 2 + 1] = poseLandmarks[i].y()
        }

        // Hand detection
        val handResult: HandLandmarkerResult = handLandmarker.detect(mpImage)
        var leftHandArray: FloatArray? = null
        var rightHandArray: FloatArray? = null

        for (i in handResult.handednesses().indices) {
            val handedness = handResult.handednesses()[i]
            val landmarks = handResult.landmarks()[i]
            val array = FloatArray(21 * 2)
            for (j in 0 until minOf(21, landmarks.size)) {
                array[j * 2] = landmarks[j].x()
                array[j * 2 + 1] = landmarks[j].y()
            }

            // MediaPipe reports handedness from the camera's perspective.
            // For front camera, "Left" label means user's right hand, so we swap.
            val label = handedness[0].categoryName()
            if (isFrontCamera) {
                if (label == "Left") rightHandArray = array
                else leftHandArray = array
            } else {
                if (label == "Left") leftHandArray = array
                else rightHandArray = array
            }
        }

        // Face detection
        val faceResult: FaceLandmarkerResult = faceLandmarker.detect(mpImage)
        var faceArray: FloatArray? = null
        if (faceResult.faceLandmarks().isNotEmpty()) {
            val faceLandmarks = faceResult.faceLandmarks()[0]
            faceArray = FloatArray(468 * 2)
            for (i in 0 until minOf(468, faceLandmarks.size)) {
                faceArray[i * 2] = faceLandmarks[i].x()
                faceArray[i * 2 + 1] = faceLandmarks[i].y()
            }
        }

        // Log extraction counts for debugging
        try {
            val poseCount = poseLandmarks.size
            val handsDetected = handResult.landmarks().size
            val faceDetected = if (faceResult.faceLandmarks().isNotEmpty()) 1 else 0
            Log.d(TAG, "Extracted landmarks: pose=$poseCount hands=$handsDetected face=$faceDetected")
        } catch (_: Exception) {
        }

        return LandmarkResult(poseArray, faceArray, leftHandArray, rightHandArray)
    }

    fun close() {
        poseLandmarker.close()
        handLandmarker.close()
        faceLandmarker.close()
    }

    data class LandmarkResult(
        val pose: FloatArray,
        val face: FloatArray?,
        val leftHand: FloatArray?,
        val rightHand: FloatArray?
    )
}
