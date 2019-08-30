package com.vidyo.vidyoconnector.share.model;

import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Data class for storing and passing screen configurations for screen capture entity.
 */
public class ShareConfiguration {

    public final int width;
    public final int height;
    public final int density;

    private ShareConfiguration(int width, int height, int density) {
        this.width = width;
        this.height = height;
        this.density = density;
    }

    public static ShareConfiguration create(WindowManager manager) {
        final DisplayMetrics metrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(metrics);
        final Point point = new Point();
        manager.getDefaultDisplay().getRealSize(point);
        return new ShareConfiguration(point.x, point.y, metrics.densityDpi);
    }
}