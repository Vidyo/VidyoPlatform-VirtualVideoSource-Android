package com.vidyo.vidyoconnector.capture

import android.annotation.SuppressLint
import android.app.Activity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleOwner
import com.vidyo.VidyoClient.Connector.Connector
import com.vidyo.VidyoClient.Device.Device
import com.vidyo.VidyoClient.Device.VideoFrame
import com.vidyo.VidyoClient.Device.VirtualVideoSource
import com.vidyo.VidyoClient.Endpoint.MediaFormat

class CaptureManager(lifecycleOwner: LifecycleOwner,
                     activity: Activity,
                     private val vidyoConnector: Connector) : Connector.IRegisterVirtualVideoSourceEventListener, ImageAnalysis.Analyzer {

    private var isVirtualCameraShareStarted = false
    private var virtualCamera: VirtualVideoSource? = null

    private val cameraSource = CameraSource(activity, this)

    private var isCameraFront: Boolean = true

    init {
        vidyoConnector.createVirtualVideoSource(VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA,
                "Virtual_Camera_23406002346",
                "CameraX")

        cameraSource.initCamera(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA)
    }

    override fun onVirtualVideoSourceAdded(virtualVideoSource: VirtualVideoSource?) {
        virtualVideoSource?.let {
            if (virtualVideoSource.type == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA) {
                virtualCamera = virtualVideoSource
                vidyoConnector.selectVirtualCamera(virtualCamera)
            }
        }
    }

    override fun onVirtualVideoSourceRemoved(virtualVideoSource: VirtualVideoSource?) {
        virtualVideoSource?.let {
            if (virtualVideoSource.type == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA) {
                virtualCamera = null
                vidyoConnector.selectVirtualCamera(null)
            }
        }
    }

    override fun onVirtualVideoSourceStateUpdated(virtualVideoSource: VirtualVideoSource?, deviceState: Device.DeviceState?) {
        when (deviceState) {
            Device.DeviceState.VIDYO_DEVICESTATE_Started -> {
                virtualVideoSource?.let {
                    if (virtualVideoSource.type == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA) {
                        isVirtualCameraShareStarted = true
                    }
                }
            }

            Device.DeviceState.VIDYO_DEVICESTATE_Stopped -> {
                virtualVideoSource?.let {
                    if (virtualVideoSource.type == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA) {
                        isVirtualCameraShareStarted = false
                    }
                }
            }

            Device.DeviceState.VIDYO_DEVICESTATE_ConfigurationChanged -> {

            }
        }
    }

    override fun onVirtualVideoSourceExternalMediaBufferReleased(p0: VirtualVideoSource?, p1: ByteArray?, p2: Long) {
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(image: ImageProxy) {


        if (isVirtualCameraShareStarted) {

            val yBuffer = image.planes[0].buffer // Y
            val uBuffer = image.planes[1].buffer // U
            val vBuffer = image.planes[2].buffer // V

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val newVideoFrame = VideoFrame(MediaFormat.VIDYO_MEDIAFORMAT_NV21, nv21, nv21.size, image.width, image.height)
            virtualCamera?.onFrame(newVideoFrame, MediaFormat.VIDYO_MEDIAFORMAT_NV21)
        }

        image.close()
    }
}