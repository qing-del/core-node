package com.jacolp.web.annotation;

import java.lang.annotation.*;

/**
 * {@link RateLimit} 容器注解，由 Java 编译器自动生成，无需手动使用。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimits {
    RateLimit[] value();
}
