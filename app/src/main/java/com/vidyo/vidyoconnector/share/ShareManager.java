package com.vidyo.vidyoconnector.share;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;

import androidx.annotation.WorkerThread;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Device.Device;
import com.vidyo.VidyoClient.Device.VideoFrame;
import com.vidyo.VidyoClient.Device.VirtualVideoSource;
import com.vidyo.VidyoClient.Endpoint.MediaFormat;
import com.vidyo.vidyoconnector.share.capture.ShareSession;
import com.vidyo.vidyoconnector.share.capture.ShareSessionListener;
import com.vidyo.vidyoconnector.share.model.FrameHolder;
import com.vidyo.vidyoconnector.share.model.FrameIntervals;
import com.vidyo.vidyoconnector.share.model.ShareConstraints;
import com.vidyo.vidyoconnector.share.provider.FrameProvider;
import com.vidyo.vidyoconnector.share.provider.FrameProviderListener;
import com.vidyo.vidyoconnector.utils.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ShareManager implements Connector.IRegisterVirtualVideoSourceEventListener, ShareSessionListener, FrameProviderListener {

    public interface Listener {

        void onShareStarted();

        void onShareStopped();

        void onError(String message);
    }

    private static final MediaFormat MEDIA_FORMAT = MediaFormat.VIDYO_MEDIAFORMAT_RGBA;
    private static final FrameIntervals FRAME_INTERVALS = new FrameIntervals();

    private static final int SCREEN_SHARE_REQUEST_CODE = 5;

    private Activity activity;
    private Listener shareListener;

    private Connector connector;
    private final MediaProjectionManager projectionManager;

    private final ShareSession shareCaptureSession;
    private final FrameProvider frameProvider;

    private Intent captureIntent;

    private VirtualVideoSource virtualVideoSource;
    private ShareConstraints shareConstraints;

    private boolean isSharing;
    private final boolean isShareAvailable;

    private boolean isBounded;

    /**
     * Executor that responsible for tasks with frame sending
     * It is restricted to the queue with max 2 tasks and any new tasks will be skipped,
     * in order to avoid queue overflow that can't be handled by the sdk
     */
    private final ExecutorService frameTaskExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(2), new ThreadPoolExecutor.DiscardPolicy());

    public ShareManager(Activity activity, Connector connector) {
        this.activity = activity;
        this.connector = connector;
        this.projectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        this.shareCaptureSession = new ShareSession();
        this.frameProvider = new FrameProvider();

        if (!this.connector.registerVirtualVideoSourceEventListener(this)) {
            Logger.e("Cannot register source listener.");
            isShareAvailable = false;
        } else isShareAvailable = true;

        this.connector.createVirtualVideoSource(VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_SHARE,
                "Virtual_Share_23406002346", "Virtual Share");
    }

    public void setShareListener(Listener listener) {
        shareListener = listener;
    }

    /**
     * Request share permissions after crop area obtained.
     */
    public void requestShare() {
        if (connector.getState() != Connector.ConnectorState.VIDYO_CONNECTORSTATE_Connected) {
            if (shareListener != null) shareListener.onError("Not connected.");
            return;
        }

        if (activity != null) {
            /* App has to start the foreground service before starting the activity returned from
             * MediaProjectionManager.createScreenCaptureIntent() */
            if (!isBounded) {
                ShareService.startShareService(this.activity, shareServiceConnection);
            }

            activity.startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_SHARE_REQUEST_CODE);
        }
    }

    /**
     * Stop share
     */
    public void requestStopShare() {
        if (connector != null) {
            connector.selectVirtualSourceWindowShare(null);
        }
    }

    /**
     * Handle response
     */
    public void handlePermissionsResponse(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == ShareManager.SCREEN_SHARE_REQUEST_CODE) {
            this.captureIntent = data;

            if (virtualVideoSource != null) {
                this.connector.selectVirtualSourceWindowShare(virtualVideoSource);
            } else {
                if (shareListener != null) shareListener.onError("Null source!");
            }
        } else {
            Logger.i("User denied share permission request.");
        }
    }

    public boolean isSharing() {
        return isSharing;
    }

    public void destroy() {
        stopShare();

        if (connector != null) {
            this.connector.unregisterVirtualVideoSourceEventListener();
            this.connector.selectVirtualWindowShare(null);

            this.connector = null;
        }

        this.frameProvider.destroy();
        this.shareCaptureSession.listen(null);

        this.shareListener = null;
        this.activity = null;
    }

    private void startShare() {
        if (!isShareAvailable()) {
            Logger.e("Share is not available.");
            return;
        }

        MediaProjection mediaProjection = projectionManager.getMediaProjection(Activity.RESULT_OK, captureIntent);

        /* Star capturing device frames */
        this.shareCaptureSession.init(this.activity, mediaProjection);
        this.shareCaptureSession.listen(this);

        /* Start provider to the remote */
        this.frameProvider.listen(this);
        this.frameProvider.startRestart();

        this.isSharing = true;

        if (this.shareListener != null) shareListener.onShareStarted();
    }

    public void tryUpdateShareOrientation() {
        if (!isSharing || this.activity == null) return;

        Logger.i(">> updateShareOrientation");
        this.shareCaptureSession.onCaptureOrientationChanged(this.activity);
        Logger.i("<< updateShareOrientation");
    }

    private void stopShare() {
        stopAndUnBindShareService();

        if (shareCaptureSession != null) shareCaptureSession.requestReleaseSession();
    }

    private void stopAndUnBindShareService() {
        if (isBounded && activity != null) {
            activity.unbindService(shareServiceConnection);
            ShareService.releaseShareService(activity);
            isBounded = false;
        }
    }

    private void updateBoundConstraints() {
        Logger.i("Update constraints: " + shareConstraints.toString());

        virtualVideoSource.setBoundsConstraints(FRAME_INTERVALS.maxInterval, FRAME_INTERVALS.minInterval,
                shareConstraints.maxWidth, shareConstraints.minWidth, shareConstraints.maxHeight, shareConstraints.minHeight);
    }

    private boolean isShareAvailable() {
        return isShareAvailable && this.projectionManager != null && this.shareCaptureSession != null && virtualVideoSource != null;
    }

    @Override
    public void onFrameCaptured(FrameHolder frameHolder) {
        if (frameProvider != null) {
            frameProvider.onFrameObtained(frameHolder);
        }
    }

    @Override
    @WorkerThread
    public void onPushFrame(FrameHolder frameHolder) {
        frameTaskExecutor.submit(() -> {
            if (isShareAvailable()) {
                final byte[] byteArray = frameHolder.byteArray;
                final VideoFrame newVidyoVideoFrame = new VideoFrame(MEDIA_FORMAT, byteArray, byteArray.length, frameHolder.width, frameHolder.height);

                virtualVideoSource.onFrame(newVidyoVideoFrame, MEDIA_FORMAT);
                if (ShareConstraints.shouldUpdateConstraints(shareConstraints, frameHolder)) {
                    shareConstraints = new ShareConstraints(frameHolder.width, frameHolder.height);
                    updateBoundConstraints();
                }
            }
        });
    }

    @Override
    public void onSessionStopped() {
        isSharing = false;

        if (frameProvider != null) frameProvider.stop();
        if (shareListener != null) shareListener.onShareStopped();
    }

    @Override
    public void onVirtualVideoSourceAdded(VirtualVideoSource virtualVideoSource) {
        if (virtualVideoSource.getType() == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_SHARE) {
            this.virtualVideoSource = virtualVideoSource;
            Logger.i("Virtual share added. Name: " + virtualVideoSource.getName());
        }
    }

    @Override
    public void onVirtualVideoSourceRemoved(VirtualVideoSource virtualVideoSource) {
        if (virtualVideoSource.getType() == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_SHARE) {
            this.virtualVideoSource = null;
            Logger.i("Virtual share removed. Name: " + virtualVideoSource.getName());
        }
    }

    @Override
    public void onVirtualVideoSourceStateUpdated(VirtualVideoSource virtualVideoSource, Device.DeviceState deviceState) {
        if (virtualVideoSource.getType() == VirtualVideoSource.VirtualVideoSourceType.VIDYO_VIRTUALVIDEOSOURCETYPE_SHARE) {
            Logger.i("Virtual share state updated. Name: " + virtualVideoSource.getName() + ", State: " + deviceState);

            switch (deviceState) {
                case VIDYO_DEVICESTATE_Started:
                    startShare();
                    break;
                case VIDYO_DEVICESTATE_Stopped:
                    stopShare();
                    break;
                case VIDYO_DEVICESTATE_ConfigurationChanged:
                    long fps = virtualVideoSource.getCurrentEncodeFrameInterval();
                    Logger.i("Virtual source config changed. FPS: %d", fps);
                    if (frameProvider != null) frameProvider.updateFPS(fps);
                    break;
            }
        }
    }

    @Override
    public void onVirtualVideoSourceExternalMediaBufferReleased(VirtualVideoSource virtualVideoSource, byte[] bytes, long l) {

    }

    private final ServiceConnection shareServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isBounded = true;

            if (service instanceof ShareService.ShareServiceBinder) {
                ShareService.ShareServiceBinder shareServiceBinder = (ShareService.ShareServiceBinder) service;
                shareServiceBinder.provideManager(ShareManager.this);
                shareServiceBinder.showNotification();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Disconnected.
            isBounded = false;
            Logger.i("Service has been disconnected.");
        }
    };
}