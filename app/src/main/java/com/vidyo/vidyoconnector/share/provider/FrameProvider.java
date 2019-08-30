package com.vidyo.vidyoconnector.share.provider;

import android.os.Handler;
import android.os.Message;

import com.vidyo.vidyoconnector.share.model.FrameHolder;
import com.vidyo.vidyoconnector.utils.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Class for pushing frames with predefined frame rate (FPS)
 */
public class FrameProvider implements Handler.Callback {

    private static final int PUSH_FRAME_TAG = 0x144;

    private static final int DEFAULT_FPS = 5;
    private static final long DEFAULT_INTERVAL = TimeUnit.SECONDS.toNanos(1) / DEFAULT_FPS;

    private FrameProviderListener providerListener;
    private long frameInterval = DEFAULT_INTERVAL;
    private FrameHolder lastAcquiredFrame;

    private Handler handler;
    private boolean isRunning;

    public FrameProvider() {
        this.handler = new Handler(this);
    }

    public void listen(FrameProviderListener listener) {
        this.providerListener = listener;
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == PUSH_FRAME_TAG && this.providerListener != null) {
            this.providerListener.onPushFrame(this.lastAcquiredFrame);
            if (this.isRunning) loop();
            return true;
        }

        return false;
    }

    public void startRestart() {
        clearInterval();

        this.isRunning = true;
        updateInterval();
    }

    public void stop() {
        Logger.i("stop");
        clearInterval();
    }

    public void updateFPS(long fpsNano) {
        if (fpsNano == 0) {
            Logger.w("Wrong FPS provided: " + fpsNano);
            return;
        }

        long interval = TimeUnit.NANOSECONDS.toMillis(fpsNano);
        Logger.i("Update max interval: " + interval + ", provided nano by library: " + fpsNano);

        this.frameInterval = interval;
    }

    public void onFrameObtained(FrameHolder frame) {
        this.lastAcquiredFrame = frame;
    }

    public void destroy() {
        clearInterval();

        this.handler = null;
        this.providerListener = null;
    }

    private void updateInterval() {
        if (this.handler != null && this.isRunning) loop();
    }

    private void loop() {
        long interval = TimeUnit.NANOSECONDS.toMillis(this.frameInterval);
        this.handler.sendEmptyMessageDelayed(PUSH_FRAME_TAG, interval);
    }

    private void clearInterval() {
        this.isRunning = false;

        if (this.handler != null) {
            this.handler.removeMessages(PUSH_FRAME_TAG);
        }
    }
}