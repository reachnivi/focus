package com.reachnivi.focus.move

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.reachnivi.focus.core.Exercise
import com.reachnivi.focus.core.Point
import com.reachnivi.focus.core.RepCounter
import com.reachnivi.focus.core.Reps

/**
 * Feeds camera frames to ML Kit pose detection on the device and counts reps from the joint
 * angle. Frames are analysed and dropped; nothing is stored.
 */
class PoseRepAnalyzer(
    exercise: Exercise,
    private val onUpdate: (reps: Int, bodyVisible: Boolean) -> Unit,
) : ImageAnalysis.Analyzer {

    private val counter = RepCounter(exercise)
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { pose ->
                val angle = jointAngle(pose, counter.exercise)
                onUpdate(counter.onAngle(angle), angle != null)
            }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = detector.close()

    /** The relevant joint angle on whichever side of the body the camera sees best. */
    private fun jointAngle(pose: Pose, exercise: Exercise): Double? {
        val (a, b, c) = when (exercise) {
            Exercise.SQUAT -> Triple(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE) to
                Triple(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
            Exercise.PUSHUP -> Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST) to
                Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        }.let { (left, right) ->
            listOf(left, right).maxByOrNull { side -> confidence(pose, side) }!!
        }
        val la = pose.getPoseLandmark(a) ?: return null
        val lb = pose.getPoseLandmark(b) ?: return null
        val lc = pose.getPoseLandmark(c) ?: return null
        if (minOf(la.inFrameLikelihood, lb.inFrameLikelihood, lc.inFrameLikelihood) < Reps.MIN_LANDMARK_CONFIDENCE) return null
        return Reps.angle(
            Point(la.position.x, la.position.y),
            Point(lb.position.x, lb.position.y),
            Point(lc.position.x, lc.position.y),
        )
    }

    private fun confidence(pose: Pose, side: Triple<Int, Int, Int>): Float =
        listOf(side.first, side.second, side.third).minOf { pose.getPoseLandmark(it)?.inFrameLikelihood ?: 0f }
}
