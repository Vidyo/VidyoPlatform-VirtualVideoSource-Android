package com.vidyo.vidyoconnector.share.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import com.vidyo.vidyoconnector.share.model.FrameHolder;
import com.vidyo.vidyoconnector.share.model.ShareConfiguration;
import com.vidyo.vidyoconnector.utils.Logger;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class ShareSession {

    private static final int IMAGE_READER_CONCURRENT_IMAGES_ACCESS_COUNT = 1;

    private static final int START_CAPTURE_DELAY_IN_MILLIS = 400;

    // Executor that responsible for tasks with image transformation
    private final ExecutorService imageTransformExecutorService = Executors.newSingleThreadExecutor();
    // Executor that responsible for session capture tasks
    private final ExecutorService captureExecutorService = Executors.newSingleThreadExecutor();

    private final Object imageTransformLock = new Object();
    private final Handler uiThreadHandler = new Handler(Looper.getMainLooper());
    private final BlockingQueue<Runnable> captureTaskQueue = new LinkedBlockingQueue<>();

    private WindowManager windowManager;
    private ImageReader imageReader;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ShareConfiguration shareConfig;

    private ShareSessionListener sessionCallback;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {

        @Override
        public void onStop() {
            super.onStop();
            releaseSession();
        }
    };

    public void init(Context context, MediaProjection mediaProjection) {
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.mediaProjection = mediaProjection;

        initCaptureTaskListener();
        startCapture();
    }

    public void listen(ShareSessionListener captureSessionListener) {
        this.sessionCallback = captureSessionListener;
    }

    public void onCaptureOrientationChanged() {
        Logger.i("onCaptureConfigChanged");
        stopCapture();
        startShareWithDelay();
    }

    public void requestReleaseSession() {
        Logger.i(">> requestReleaseSession");
        releaseSession();
        Logger.i("<< requestReleaseSession");
    }

    /**
     * Function that checks correct order of session tasks such as start, stop, release.
     */
    private void initCaptureTaskListener() {
        Logger.i("initCaptureTaskListener");

        captureExecutorService.submit(() -> {
            while (!isReleased()) {
                try {
                    final Runnable captureTask = captureTaskQueue.take();
                    Logger.i("captureTaskListener: captureTask exist");
                    captureTask.run();
                } catch (Exception e) {
                    Logger.e("captureTaskListener: captureTask failed " + e.getMessage());
                }
            }

            Logger.i("captureTaskListener: session is released");
        });
    }

    private boolean isReleased() {
        return mediaProjection == null;
    }

    private void startCapture() {
        Logger.i("startCapture");
        shareConfig = ShareConfiguration.create(windowManager);
        captureTaskQueue.add(() -> {
            setupReader();
            setUpVirtualDisplay();
            mediaProjection.registerCallback(projectionCallback, uiThreadHandler);
        });
    }

    private void setupReader() {
        imageReader = ImageReader.newInstance(shareConfig.width, shareConfig.height, PixelFormat.RGBA_8888, IMAGE_READER_CONCURRENT_IMAGES_ACCESS_COUNT);
        imageReader.setOnImageAvailableListener(this::processImage, uiThreadHandler);
    }

    private void processImage(ImageReader reader) {
        imageTransformExecutorService.submit(() -> {
            FrameHolder frameHolder = null;

            synchronized (imageTransformLock) {
                try (Image image = reader.acquireNextImage()) {
                    if (image != null) frameHolder = transformImageToFrame(image);
                } catch (Exception e) {
                    e.printStackTrace();
                    Logger.e(e.getMessage());
                }
            }

            if (frameHolder != null && sessionCallback != null) {
                sessionCallback.onFrameCaptured(frameHolder);
            }
        });
    }

    private void setUpVirtualDisplay() {
        Logger.i("setUpVirtualDisplay");
        final int virtualDisplayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture", shareConfig.width, shareConfig.height, shareConfig.density,
                virtualDisplayFlags, imageReader.getSurface(), null, uiThreadHandler);
    }

    /**
     * Function to start capture of share with delay.
     * We need to set reader and virtual display preparations with delay during rotation of the screen.
     * App start obtaining virtual display before there is one, so we set approximate delay.
     */
    private void startShareWithDelay() {
        Logger.i("startShareWithDelay");
        shareConfig = ShareConfiguration.create(windowManager);
        captureTaskQueue.add(() -> {
            try {
                Thread.sleep(START_CAPTURE_DELAY_IN_MILLIS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            setupReader();
            setUpVirtualDisplay();
        });
    }

    private void releaseSession() {
        Logger.i(">> releaseSession");
        captureTaskQueue.add(() -> {
            synchronized (imageTransformLock) {
                releaseReader();
                releaseVirtualDisplay();
                releaseProjection();
            }
        });

        if (sessionCallback != null) sessionCallback.onSessionStopped();
        sessionCallback = null;

        Logger.i("<< releaseSession");
    }

    private void stopCapture() {
        Logger.i("stopCapture");
        captureTaskQueue.add(() -> {
            synchronized (imageTransformLock) {
                releaseReader();
                releaseVirtualDisplay();
            }
        });
    }

    private void releaseVirtualDisplay() {
        Logger.i("releaseVirtualDisplay");
        virtualDisplay.release();
        virtualDisplay = null;
    }

    private void releaseReader() {
        Logger.i("releaseReader");
        imageReader.close();
        imageReader = null;
    }

    private void releaseProjection() {
        Logger.i("releaseProjection");
        mediaProjection.unregisterCallback(projectionCallback);
        mediaProjection.stop();
        mediaProjection = null;
    }

    /**
     * Transform image to bitmap logic.
     *
     * @param image {@link Image}
     * @return converted {@link FrameHolder}
     */
    private FrameHolder transformImageToFrame(Image image) {
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        final int pixelStride = planes[0].getPixelStride();
        final int rowStride = planes[0].getRowStride();
        final int rowPadding = rowStride - pixelStride * image.getWidth();
        final int widthDelta = rowPadding / pixelStride;
        final int width = image.getWidth() + widthDelta;
        final int height = image.getHeight();

        Bitmap finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        finalBitmap.copyPixelsFromBuffer(buffer);

        if (widthDelta > 0) {
            finalBitmap = Bitmap.createBitmap(finalBitmap, 0, 0, width - widthDelta, height);
        }

        int bytes = finalBitmap.getByteCount();
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes);
        finalBitmap.copyPixelsToBuffer(byteBuffer);
        final byte[] byteArray = byteBuffer.array();
        final FrameHolder frameHolder = new FrameHolder(byteArray, finalBitmap.getWidth(), finalBitmap.getHeight(), image.getTimestamp());
        finalBitmap.recycle();
        return frameHolder;
    }
}