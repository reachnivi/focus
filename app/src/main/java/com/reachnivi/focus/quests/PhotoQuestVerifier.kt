package com.reachnivi.focus.quests

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.reachnivi.focus.core.Label
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Labels a photo entirely on the phone with ML Kit's bundled model. Nothing leaves the device. */
class PhotoQuestVerifier {
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.3f).build())
    }

    suspend fun labels(bitmap: Bitmap): List<Label> = suspendCancellableCoroutine { cont ->
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result -> cont.resume(result.map { Label(it.text, it.confidence) }) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
