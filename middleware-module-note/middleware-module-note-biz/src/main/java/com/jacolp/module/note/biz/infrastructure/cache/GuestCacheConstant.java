package com.jacolp.module.note.biz.infrastructure.cache;

/**
 * 访客公开接口缓存常量。
 *
 * <p>这里只保存缓存名、TTL 和容量上限，方便后续从 Caffeine 切换到 Redis
 * 或配置中心时保持业务注解不变。</p>
 */
public class GuestCacheConstant {

    /** 访客公开笔记分页列表缓存。 */
    public static final String GUEST_NOTE_LIST_CACHE = "guest-note-list";

    /** 访客公开笔记详情缓存。 */
    public static final String GUEST_NOTE_DETAIL_CACHE = "guest-note-detail";

    /** 列表缓存 TTL：列表变化频率较高，保持较短时间。 */
    public static final long GUEST_NOTE_LIST_TTL_SECONDS = 300L;

    /** 详情缓存 TTL：详情内容较重，允许比列表稍长。 */
    public static final long GUEST_NOTE_DETAIL_TTL_SECONDS = 600L;

    /** 列表查询组合较多，容量控制得更保守。 */
    public static final long GUEST_NOTE_LIST_MAX_SIZE = 500L;

    /** 详情按 noteId 命中，容量可以略大。 */
    public static final long GUEST_NOTE_DETAIL_MAX_SIZE = 1000L;

    private GuestCacheConstant() {
    }
}
