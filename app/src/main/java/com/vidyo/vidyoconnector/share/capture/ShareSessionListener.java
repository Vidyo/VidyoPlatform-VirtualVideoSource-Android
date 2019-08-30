package com.vidyo.vidyoconnector.share.capture;

import android.support.annotation.WorkerThread;

import com.vidyo.vidyoconnector.share.model.FrameHolder;

public interface ShareSessionListener {

    @WorkerThread
    void onFrameCaptured(FrameHolder frameHolder);

    void onSessionStopped();
}