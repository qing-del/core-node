package com.jacolp.common.core.exception;

/**
 * 滑动窗口限流超出异常，由 {@link com.jacolp.middleware.common.web.aspect.RateLimitAspect} 在检测到
 * 请求频率超过 {@link com.jacolp.middleware.common.web.annotation.RateLimit} 阈值时抛出。
 */
public class RateLimitExceededException extends BaseException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
