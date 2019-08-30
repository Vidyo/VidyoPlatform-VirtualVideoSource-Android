package com.vidyo.vidyoconnector.share.model;

import java.util.concurrent.TimeUnit;

/**
 * FPS constraints
 */
public class FrameIntervals {

    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    private static final int DEFAULT_MAX_FPS = 10;
    private static final int DEFAULT_MIN_FPS = 5;

    public final long maxInterval;
    public final long minInterval;

    public FrameIntervals() {
        this(DEFAULT_MAX_FPS, DEFAULT_MIN_FPS);
    }

    private FrameIntervals(int maxFPS, int minFPS) {
        this.minInterval = maxFPS != 0 ? SECOND / maxFPS : 0;
        this.maxInterval = minFPS != 0 ? SECOND / minFPS : 0;
    }
}