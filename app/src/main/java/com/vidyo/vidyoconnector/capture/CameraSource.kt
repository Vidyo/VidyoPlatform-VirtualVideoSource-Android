package com.vidyo.vidyoconnector.capture

import android.app.Activity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vidyo.vidyoconnector.utils.Logger
import java.util.concurrent.Executors

class CameraSource(private val context: Activity, private val imageAnalyzer: ImageAnalysis.Analyzer) {

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    fun initCamera(lifecycleOwner: LifecycleOwner, cameraSelector: CameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val imageAnalyzerLocal = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, imageAnalyzer)
                    }
            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalyzerLocal)
            } catch (exc: Exception) {
                Logger.e("initCamera: Camera use case binding failed $exc")
            }
        }, ContextCompat.getMainExecutor(context))
    }
}