package com.jacolp.common.web.annotation;

import java.lang.annotation.*;

/**
 * 滑动窗口限流注解，可重复使用以实现多层嵌套限流。
 *
 * <pre>{@code
 *   // 每分钟 1 次 + 每小时 3 次
 *   @RateLimit(windowSeconds = 60,  maxRequests = 1)
 *   @RateLimit(windowSeconds = 3600, maxRequests = 3)
 *   public Result<String> someEndpoint() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {
    /** 滑动窗口时长，单位：秒 */
    long windowSeconds();

    /** 窗口内最多允许的请求次数 */
    int maxRequests();

    /** 滑动窗口的前缀 */
    String prefix();
}
