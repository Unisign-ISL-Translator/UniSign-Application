package com.unisign.unisign.logic

import kotlin.math.*

/**
 * Normalizes raw landmarks relative to nose position and shoulder width,
 * replicating the Python normalize_frame() logic from live_detect.py.
 */
object LandmarkNormalizer {

    /**
     * Normalize a single frame's landmarks.
     *
     * @param poseLandmarks 33 pose landmarks as (x, y) pairs (flat array of size 66)
     * @param faceLandmarks 468 face landmarks as (x, y) pairs, or null
     * @param leftHandLandmarks 21 hand landmarks as (x, y) pairs, or null
     * @param rightHandLandmarks 21 hand landmarks as (x, y) pairs, or null
     * @return FloatArray of size 138 (69 joints * 2 coords) or null if pose unavailable
     */
    fun normalizeFrame(
        poseLandmarks: FloatArray?,
        faceLandmarks: FloatArray?,
        leftHandLandmarks: FloatArray?,
        rightHandLandmarks: FloatArray?
    ): FloatArray? {
        if (poseLandmarks == null || poseLandmarks.size < 66) return null

        // Nose (index 0) for root translation
        val rootX = poseLandmarks[0]
        val rootY = poseLandmarks[1]

        // Left shoulder (index 11), Right shoulder (index 12) for scale + rotation
        val leftShoulderX = poseLandmarks[11 * 2]
        val leftShoulderY = poseLandmarks[11 * 2 + 1]
        val rightShoulderX = poseLandmarks[12 * 2]
        val rightShoulderY = poseLandmarks[12 * 2 + 1]

        val shoulderWidth = hypot(
            (leftShoulderX - rightShoulderX).toDouble(),
            (leftShoulderY - rightShoulderY).toDouble()
        ).toFloat()

        if (shoulderWidth < 1e-6f) return null

        // Shoulder rotation alignment
        val dx = rightShoulderX - leftShoulderX
        val dy = rightShoulderY - leftShoulderY
        val angle = atan2(dy.toDouble(), dx.toDouble())
        val cosA = cos(-angle).toFloat()
        val sinA = sin(-angle).toFloat()

        val result = FloatArray(69 * 2)
        var offset = 0

        // Left hand (21 joints) - indices 0..20
        if (leftHandLandmarks != null && leftHandLandmarks.size >= 42) {
            for (i in 0 until 21) {
                val (nx, ny) = normalizePoint(
                    leftHandLandmarks[i * 2], leftHandLandmarks[i * 2 + 1],
                    rootX, rootY, cosA, sinA, shoulderWidth
                )
                result[offset++] = nx
                result[offset++] = ny
            }
        } else {
            offset += 42 // leave as 0.0
        }

        // Right hand (21 joints) - indices 21..41
        if (rightHandLandmarks != null && rightHandLandmarks.size >= 42) {
            for (i in 0 until 21) {
                val (nx, ny) = normalizePoint(
                    rightHandLandmarks[i * 2], rightHandLandmarks[i * 2 + 1],
                    rootX, rootY, cosA, sinA, shoulderWidth
                )
                result[offset++] = nx
                result[offset++] = ny
            }
        } else {
            offset += 42 // leave as 0.0
        }

        // Face (13 joints) - specific indices from face mesh
        val faceIndices = intArrayOf(0, 1, 4, 10, 33, 61, 133, 152, 263, 291, 362, 13, 14)
        if (faceLandmarks != null && faceLandmarks.size >= 936) { // at least 468 landmarks * 2
            for (idx in faceIndices) {
                val (nx, ny) = normalizePoint(
                    faceLandmarks[idx * 2], faceLandmarks[idx * 2 + 1],
                    rootX, rootY, cosA, sinA, shoulderWidth
                )
                result[offset++] = nx
                result[offset++] = ny
            }
        } else {
            offset += 26 // leave as 0.0
        }

        // Pose (14 joints) - specific indices
        val poseIndices = intArrayOf(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
        for (idx in poseIndices) {
            val (nx, ny) = normalizePoint(
                poseLandmarks[idx * 2], poseLandmarks[idx * 2 + 1],
                rootX, rootY, cosA, sinA, shoulderWidth
            )
            result[offset++] = nx
            result[offset++] = ny
        }

        return result
    }

    private fun normalizePoint(
        x: Float, y: Float,
        rootX: Float, rootY: Float,
        cosA: Float, sinA: Float,
        shoulderWidth: Float
    ): Pair<Float, Float> {
        // Translate to nose root
        val nx = x - rootX
        val ny = y - rootY
        // Rotate to align shoulders
        val rx = nx * cosA - ny * sinA
        val ry = nx * sinA + ny * cosA
        // Scale by shoulder width
        return Pair(rx / shoulderWidth, ry / shoulderWidth)
    }
}
