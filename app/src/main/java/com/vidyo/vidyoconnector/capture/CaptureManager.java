package com.vidyo.vidyoconnector.capture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Device.Device;
import com.vidyo.VidyoClient.Device.VideoFrame;
import com.vidyo.VidyoClient.Device.VirtualVideoSource;
import com.vidyo.VidyoClient.Endpoint.MediaFormat;
import com.vidyo.vidyoconnector.capture.model.CaptureConstraints;
import com.vidyo.vidyoconnector.capture.model.FrameIntervals;
import com.vidyo.vidyoconnector.utils.Logger;

public class CaptureManager implements Connector.IRegisterVirtualVideoSourceEventListener, FrameProcessor {

    private static final MediaFormat MEDIA_FORMAT = MediaFormat.VIDYO_MEDIAFORMAT_RGBA;
    private static final FrameIntervals FRAME_INTERVALS = new FrameIntervals();

    private Connector connector;

    @Nullable
    private final CameraView cameraView;

    private VirtualVideoSource virtualVideoSource;
    private CaptureConstraints captureConstraints;

    private boolean isCapturing = false;

    public CaptureManager(Connector connector, @Nullable CameraView cameraView) {
        this.connector = connector;
        this.cameraView = cameraView;

        if (!this.connector.registerVirtualVideoSourceEventListener(this)) {
            Logger.e("Cannot register source listener.");
        }

        this.connector.createVirtualVideoSource(VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA,
                "Virtual_Camera_23406002346", "Virtual Camera");
    }

    /**
     * Start capture from local camera
     */
    public void requestStartCamera() {
        if (virtualVideoSource != null) {
            this.connector.selectVirtualCamera(virtualVideoSource);
        }
    }

    /**
     * Stop capture from local camera
     */
    public void requestStopCamera() {
        if (connector != null) {
            connector.selectVirtualCamera(null);
        }
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public void destroy() {
        stopCapture();

        if (connector != null) {
            this.connector.unregisterVirtualVideoSourceEventListener();
            this.connector.selectVirtualCamera(null);

            this.connector = null;
        }
    }

    private boolean startCapture() {
        if (cameraView == null) return false;

        cameraView.addFrameProcessor(this);
        isCapturing = true;
        return true;
    }

    private boolean stopCapture() {
        if (cameraView == null) return false;

        cameraView.removeFrameProcessor(this);
        isCapturing = false;
        return true;
    }

    private void updateBoundConstraints() {
        Logger.i("Update constraints: " + captureConstraints.toString());
        virtualVideoSource.setBoundsConstraints(FRAME_INTERVALS.maxInterval, FRAME_INTERVALS.minInterval,
                captureConstraints.maxWidth, captureConstraints.minWidth, captureConstraints.maxHeight, captureConstraints.minHeight);
    }

    @Override
    public void process(@NonNull Frame frame) {
        int width = frame.getSize().getWidth();
        int height = frame.getSize().getHeight();

        Logger.i("Capture frame: %dx%d", width, height);

        if (captureConstraints == null || CaptureConstraints.shouldUpdateConstraints(captureConstraints, frame)) {
            captureConstraints = new CaptureConstraints(width, height);
            updateBoundConstraints();
        }

        final VideoFrame newVideoFrame = new VideoFrame(
                MediaFormat.VIDYO_MEDIAFORMAT_NV21,
                frame.getData(),
                ((byte[]) frame.getData()).length,
                width,
                height
        );

        virtualVideoSource.onFrame(newVideoFrame, MediaFormat.VIDYO_MEDIAFORMAT_NV21);
    }

    @Override
    public void onVirtualVideoSourceAdded(VirtualVideoSource virtualVideoSource) {
        if (virtualVideoSource.getType() == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA) {
            this.virtualVideoSource = virtualVideoSource;
            Logger.i("Virtual camera added. Name: " + virtualVideoSource.getName());
        }
    }

    @Override
    public void onVirtualVideoSourceRemoved(VirtualVideoSource virtualVideoSource) {
        if (virtualVideoSource.getType() == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA) {
            this.virtualVideoSource = null;
            Logger.i("Virtual camera removed. Name: " + virtualVideoSource.getName());
        }
    }

    @Override
    public void onVirtualVideoSourceStateUpdated(VirtualVideoSource virtualVideoSource, Device.DeviceState deviceState) {
        if (virtualVideoSource.getType() == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_CAMERA) {
            Logger.i("Virtual camera state updated. Name: " + virtualVideoSource.getName());

            switch (deviceState) {
                case VIDYO_DEVICESTATE_Started:
                    startCapture();
                    break;
                case VIDYO_DEVICESTATE_Stopped:
                    stopCapture();
                    break;
                case VIDYO_DEVICESTATE_ConfigurationChanged:
                    long fps = virtualVideoSource.getCurrentEncodeFrameInterval();
                    Logger.i("Virtual source config changed. FPS: %d", fps);

                    if (cameraView != null) {
                        // TODO: Set suggested frame rate!
                    }
                    break;
            }
        }
    }

    @Override
    public void onVirtualVideoSourceExternalMediaBufferReleased(VirtualVideoSource virtualVideoSource, byte[] bytes, long l) {

    }
}