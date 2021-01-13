package com.vidyo.vidyoconnector.capture.model;

import com.otaliastudios.cameraview.frame.Frame;

/**
 * Capture constraints for remote delivery.
 */
public class CaptureConstraints {

    private static final float DEFAULT_SCALE = 1;

    // Full HD
    private static final int MAX_VIRTUAL_CAPTURE_QUALITY = 1080;

    private static final float SCALE_MIN_QUALITY_DELTA = 1.5f;

    public final int maxWidth;
    public final int minWidth;
    public final int maxHeight;
    public final int minHeight;

    private final int originalWidth;
    private final int originalHeight;

    public CaptureConstraints(int width, int height) {
        originalWidth = width;
        originalHeight = height;

        final float maxScaleFactor = getScaleFactor(width, height, MAX_VIRTUAL_CAPTURE_QUALITY);
        maxWidth = (int) (width * maxScaleFactor);
        maxHeight = (int) (height * maxScaleFactor);

        final float minScaleFactor = maxScaleFactor / SCALE_MIN_QUALITY_DELTA;
        minWidth = (int) (width * minScaleFactor);
        minHeight = (int) (height * minScaleFactor);
    }

    private static float getScaleFactor(int width, int height, int quality) {
        final int side = width < height ? width : height; // check with what side we should work in order to get delta
        return side < quality ? DEFAULT_SCALE : (float) quality / side;
    }

    /* Usually happens after rotation */
    public static boolean shouldUpdateConstraints(CaptureConstraints constraints, Frame frame) {
        return constraints == null || constraints.originalWidth != frame.getSize().getWidth() || constraints.originalHeight != frame.getSize().getHeight();
    }

    @Override
    public String toString() {
        return "CaptureConstraints{" +
                "maxWidth=" + maxWidth +
                ", minWidth=" + minWidth +
                ", maxHeight=" + maxHeight +
                ", minHeight=" + minHeight +
                ", originalWidth=" + originalWidth +
                ", originalHeight=" + originalHeight +
                '}';
    }
}