package com.example.barcodescanner.utils

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.barcodescanner.ui.viewmodel.ScanViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrAnalyzer(
    private val viewModel: ScanViewModel
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastProcessTime = 0L
    private val processInterval = 400L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < processInterval) {
            imageProxy.close()
            return
        }
        lastProcessTime = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (result.text.isNotBlank()) {
                    val confidences = mutableListOf<Float>()
                    result.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            line.elements.forEach { element ->
                                element.confidence?.let { confidences.add(it) }
                            }
                        }
                    }
                    val avgConfidence = if (confidences.isNotEmpty()) {
                        confidences.average().toFloat()
                    } else null

                    mainHandler.post {
                        viewModel.onTextDetected(result.text, avgConfidence)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("OcrAnalyzer", "OCR failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
