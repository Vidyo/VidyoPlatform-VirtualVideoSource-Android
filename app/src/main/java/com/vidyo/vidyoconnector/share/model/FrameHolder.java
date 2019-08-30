package com.vidyo.vidyoconnector.share.model;

/**
 * Hold/transfer frame information
 */
public class FrameHolder {

    public final byte[] byteArray;
    public final int width;
    public final int height;
    public final long timestamp;

    public FrameHolder(byte[] byteArray, int width, int height, long timestamp) {
        this.byteArray = byteArray;
        this.width = width;
        this.height = height;
        this.timestamp = timestamp;
    }
}