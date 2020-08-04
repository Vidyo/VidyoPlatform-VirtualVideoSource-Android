package com.vidyo.vidyoconnector.share.provider;

import androidx.annotation.MainThread;

import com.vidyo.vidyoconnector.share.model.FrameHolder;

public interface FrameProviderListener {

    /**
     * Notify logic that frame has to be pushed by interval as FPS.
     * Handle next action on background thread.
     *
     * @param frameHolder {@link FrameHolder} frame to be pushed to remote.
     */
    @MainThread
    void onPushFrame(FrameHolder frameHolder);
}