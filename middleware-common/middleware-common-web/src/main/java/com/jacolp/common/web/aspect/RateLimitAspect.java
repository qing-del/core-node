package com.jacolp.common.web.aspect;

import java.time.Duration;
import java.util.UUID;

import com.jacolp.common.web.annotation.RateLimit;
import com.jacolp.common.web.annotation.RateLimits;
import com.jacolp.common.core.exception.RateLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jacolp.common.security.context.BaseContext;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 滑动窗口限流切面。
 *
 * <p>基于 Redis ZSET 实现：以当前时间戳为 score 和 member 写入有序集合，
 * 同时清除窗口外的旧记录，统计剩余条数判断是否超限。</p>
 *
 * <p>支持方法上叠加多个 {@link RateLimit}（嵌套限流），任一层触发则立即拒绝。</p>
 */
@Aspect
@Component
@Order(0) // 优先级最高
@Slf4j
public class RateLimitAspect {
    @Autowired
    private StringRedisTemplate redis;

    @Pointcut("@annotation(com.jacolp.common.web.annotation.RateLimit) || "
            + "@annotation(com.jacolp.common.web.annotation.RateLimits)")
    public void rateLimitPointcut() {
    }

    @Around("rateLimitPointcut()")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // 收集方法上的所有 @RateLimit（支持重复注解）
        RateLimits container = signature.getMethod().getAnnotation(RateLimits.class);
        RateLimit single = signature.getMethod().getAnnotation(RateLimit.class);

        RateLimit[] limits;
        if (container != null) {
            limits = container.value();
        } else if (single != null) {
            limits = new RateLimit[] { single };
        } else {
            return joinPoint.proceed();
        }

        // 逐层检查
        for (RateLimit limit : limits) {
            checkWindow(limit, signature);
        }

        // 全部通过后再递增计数（避免部分通过部分拒绝导致计数不一致）
        for (RateLimit limit : limits) {
            recordRequest(limit, signature);
        }

        return joinPoint.proceed();
    }

    private void checkWindow(RateLimit limit, MethodSignature signature) {
        String key = buildKey(limit, signature);
        long now = System.currentTimeMillis();
        long windowStart = now - Duration.ofSeconds(limit.windowSeconds()).toMillis();

        // 移除窗口外的旧记录
        redis.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 统计窗口内剩余请求数
        Long count = redis.opsForZSet().zCard(key);
        if (count != null && count >= limit.maxRequests()) {
            log.warn("Rate limit exceeded: {} — window={}s, max={}, current={}",
                    key, limit.windowSeconds(), limit.maxRequests(), count);
            throw new RateLimitExceededException(
                    String.format("请求过于频繁，%d 秒内最多 %d 次，请稍后再试",
                            limit.windowSeconds(), limit.maxRequests()));
        }
    }

    private void recordRequest(RateLimit limit, MethodSignature signature) {
        String key = buildKey(limit, signature);
        long now = System.currentTimeMillis();
        long windowSeconds = limit.windowSeconds();

        // 使用唯一成员值避免 ZADD 覆盖（同一毫秒内多个请求不会互相覆盖）
        String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);
        redis.opsForZSet().add(key, member, now);

        // 设置 key 过期时间，避免僵尸 key 堆积
        redis.expire(key, Duration.ofSeconds(windowSeconds + 10));
    }

    /**
     * 如果是没有登录信息上下文就通过 ip 来进行限流
     * @param limit
     * @param signature
     * @return
     */
    private String buildKey(RateLimit limit, MethodSignature signature) {
        Long userId = BaseContext.getCurrentIdWithoutValid();
        String methodKey = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String identity = userId != null ? "user:" + userId : "ip:" + getClientIp();
        return limit.prefix() + ":" + methodKey + ":" + identity + ":" + limit.windowSeconds();
    }

    private String getClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "anonymous";
        }
        HttpServletRequest request = attributes.getRequest();
        // 尝试从 CloudFlare 获取真实 IP
        String cfConnectingIp = firstValidHeaderValue(request.getHeader("CF-Connecting-IP"));
        if (cfConnectingIp != null) {
            return cfConnectingIp;
        }

        // 尝试从 Nginx 获取真实 IP
        String realIp = firstValidHeaderValue(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        // 尝试从 X-Forwarded-For 获取真实 IP
        String forwardedFor = firstValidForwardedFor(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor;
        }
        return request.getRemoteAddr();
    }

    private String firstValidForwardedFor(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        String[] candidates = forwardedFor.split(",");
        for (String candidate : candidates) {
            String valid = firstValidHeaderValue(candidate);
            if (valid != null) {
                return valid;
            }
        }
        return null;
    }

    private String firstValidHeaderValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String value = headerValue.trim();
        if ("unknown".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}
