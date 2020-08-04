package com.vidyo.vidyoconnector.share.capture;

import androidx.annotation.WorkerThread;

import com.vidyo.vidyoconnector.share.model.FrameHolder;

public interface ShareSessionListener {

    @WorkerThread
    void onFrameCaptured(FrameHolder frameHolder);

    void onSessionStopped();
}