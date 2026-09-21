package com.jacolp.document.application.fileindex;

import java.util.Objects;
import java.util.UUID;

/** Canal 到 RabbitMQ 的最小 file 刷新消息，只携带资源定位，不携带旧值或权限列表。 */
public record FileIndexRefreshMessage(String eventType, String resourceType, String resourceId, String eventId) {

    /** 稳定的增量投影事件类型。 */
    public static final String EVENT_TYPE = "FILE_INDEX_REFRESH";

    /** 校验消息结构，避免无效事件进入投影重试链路后产生模糊错误。 */
    public FileIndexRefreshMessage {
        if (!EVENT_TYPE.equals(eventType)) {
            throw new IllegalArgumentException("unsupported file index event type: " + eventType);
        }
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        try {
            FileIndexResourceType.valueOf(resourceType);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported file index resource type: " + resourceType, exception);
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        try {
            if (Long.parseLong(resourceId) <= 0) {
                throw new NumberFormatException("non-positive");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("resourceId must be a positive integer", exception);
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("eventId must be a UUID", exception);
        }
    }

    /** 根据资源键创建新的 UUID 事件；事件重复投递仍由稳定资源 ID 保证幂等。 */
    public static FileIndexRefreshMessage forResource(FileIndexResourceKey resourceKey) {
        Objects.requireNonNull(resourceKey, "resourceKey must not be null");
        return new FileIndexRefreshMessage(EVENT_TYPE, resourceKey.resourceType().name(),
                String.valueOf(resourceKey.resourceId()),
                UUID.randomUUID().toString());
    }

    /** 转换为投影服务使用的资源键。 */
    public FileIndexResourceKey resourceKey() {
        return new FileIndexResourceKey(FileIndexResourceType.valueOf(resourceType), Long.parseLong(resourceId));
    }
}
