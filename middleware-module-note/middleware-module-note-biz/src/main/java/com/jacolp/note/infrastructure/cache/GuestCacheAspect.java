package com.jacolp.note.infrastructure.cache;

import java.util.Arrays;

import com.jacolp.note.application.dto.note.PublicNoteQueryDTO;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 访客公开接口缓存切面。
 *
 * <p>该切面只负责缓存编排：构造稳定 key、调用 {@link CacheOperator}、
 * 在写操作成功后清理缓存。真正的存储实现由 {@link CacheOperator} 决定。</p>
 */
@Aspect
@Component
@Order(3)
@Slf4j
public class GuestCacheAspect {

    @Autowired private CacheOperator cacheOperator;

    /**
     * 处理访客读缓存。
     *
     * <p>业务异常会从 {@link CacheOperator#get} 透传出来，不会进入缓存。</p>
     */
    @Around("@annotation(guestCacheable)")
    public Object cacheable(ProceedingJoinPoint joinPoint, GuestCacheable guestCacheable) throws Throwable {
        String cacheName = guestCacheable.cacheName();
        String key = buildCacheKey(cacheName, joinPoint);
        return cacheOperator.get(cacheName, key, joinPoint::proceed, guestCacheable.ttlSeconds());
    }

    /**
     * 处理访客缓存失效。
     *
     * <p>目前只标注在公开/下架入口。使用 {@code @AfterReturning}，
     * 只有目标写方法成功返回后才会清理缓存；
     * 事务回滚或业务异常不会误清理。</p>
     */
    @AfterReturning("@annotation(guestCacheEvict)")
    public void evict(GuestCacheEvict guestCacheEvict) {
        for (String cacheName : guestCacheEvict.cacheNames()) {
            if (!StringUtils.hasText(cacheName)) {
                continue;
            }
            if (guestCacheEvict.allEntries()) {
                cacheOperator.clear(cacheName);
                continue;
            }
            log.warn("Guest cache evict ignored because allEntries=false has no explicit key, cacheName: {}", cacheName);
        }
    }

    /**
     * 构造缓存 key。
     *
     * <p>访客笔记列表和详情有固定 key 规则，避免 DTO 默认 {@code toString()}
     * 或参数对象地址导致缓存不可复用。未知缓存名使用通用兜底规则。</p>
     */
    private String buildCacheKey(String cacheName, ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (GuestCacheConstant.GUEST_NOTE_LIST_CACHE.equals(cacheName)) {
            return buildGuestNoteListKey(cacheName, args);
        }
        if (GuestCacheConstant.GUEST_NOTE_DETAIL_CACHE.equals(cacheName)) {
            return buildGuestNoteDetailKey(cacheName, args);
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return cacheName + ":" + signature.getDeclaringType().getName() + "." + signature.getName()
                + ":" + Arrays.deepToString(args);
    }

    /**
     * 构造访客公开笔记列表缓存 key。
     *
     * <p>keyword 会 trim，分页参数使用 DTO 默认值，确保
     * {@code pageNum=null} 与业务实际默认页码使用同一个 key。</p>
     */
    private String buildGuestNoteListKey(String cacheName, Object[] args) {
        PublicNoteQueryDTO dto = null;
        for (Object arg : args) {
            if (arg instanceof PublicNoteQueryDTO queryDTO) {
                dto = queryDTO;
                break;
            }
        }
        PublicNoteQueryDTO query = dto == null ? new PublicNoteQueryDTO() : dto;
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : "";
        String topicId = query.getTopicId() == null ? "" : query.getTopicId().toString();
        return cacheName
                + ":keyword=" + keyword
                + ":topicId=" + topicId
                + ":pageNum=" + query.getPageNumOrDefault()
                + ":pageSize=" + query.getPageSizeOrDefault();
    }

    /**
     * 构造访客公开笔记详情缓存 key。
     */
    private String buildGuestNoteDetailKey(String cacheName, Object[] args) {
        Long noteId = null;
        for (Object arg : args) {
            if (arg instanceof Long id) {
                noteId = id;
                break;
            }
        }
        return cacheName + ":noteId=" + (noteId == null ? "" : noteId);
    }
}
