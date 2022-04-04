package com.vidyo.vidyoconnector.share.model;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;

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

    public static ShareConfiguration create(Context context, WindowManager windowManager) {
        Configuration configuration = context.getResources().getConfiguration();

        final Point point = new Point();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            final DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            windowManager.getDefaultDisplay().getRealSize(point);
        } else {
            Rect maxMetrics = windowManager.getMaximumWindowMetrics().getBounds();
            point.set(maxMetrics.width(), maxMetrics.height());
        }

        return new ShareConfiguration(point.x, point.y, configuration.densityDpi);
    }

    @NonNull
    @Override
    public String toString() {
        return "ShareConfiguration{" + "width=" + width + ", height=" + height + ", density=" + density + '}';
    }
}