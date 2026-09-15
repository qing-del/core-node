package com.jacolp.audio.biz.constant;

import java.util.Set;

public final class AudioConstant {
    private AudioConstant() { }

    public static final String REDIS_STREAM_KEY = "stream:audio:tasks";
    public static final String STREAM_GROUP = "audio-consumer-group";
    public static final int TASK_STATUS_PENDING = 0;
    public static final int TASK_STATUS_PROCESSING = 1;
    public static final int TASK_STATUS_SUCCESS = 2;
    public static final int TASK_STATUS_FAILED = -1;
    public static final int TASK_TIMEOUT_MINUTES = 10;
    public static final float DEFAULT_NOISE_FACTOR = 0.5f;
    public static final String NOISE_TYPE_PURE = "PURE";
    public static final String NOISE_TYPE_WHITE_NOISE = "WHITE_NOISE";
    public static final String NOISE_TYPE_PINK_NOISE = "PINK_NOISE";
    public static final String NOISE_TYPE_BROWN_NOISE = "BROWN_NOISE";
    public static final String NOISE_TYPE_CAFE = "CAFE";
    public static final String NOISE_TYPE_AIRPORT = "AIRPORT";
    public static final String NOISE_TYPE_SUBWAY = "SUBWAY";
    public static final Set<String> VALID_NOISE_TYPES = Set.of(NOISE_TYPE_PURE, NOISE_TYPE_WHITE_NOISE,
            NOISE_TYPE_PINK_NOISE, NOISE_TYPE_BROWN_NOISE, NOISE_TYPE_CAFE, NOISE_TYPE_AIRPORT, NOISE_TYPE_SUBWAY);
}
