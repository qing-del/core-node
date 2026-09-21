package com.jacolp.document.infrastructure.redis;

/** LINK 原子双流入队返回的两个 Redis Stream ID。 */
public record DocumentLinkAcceptedRedisOperations(
        /** updates Stream 的条目 ID。 */
        String updatesRedisOpId,
        /** binding Stream 的条目 ID。 */
        String bindingRedisOpId) {

    public DocumentLinkAcceptedRedisOperations {
        if (updatesRedisOpId == null || updatesRedisOpId.isBlank()) {
            throw new IllegalArgumentException("updatesRedisOpId must not be blank");
        }
        if (bindingRedisOpId == null || bindingRedisOpId.isBlank()) {
            throw new IllegalArgumentException("bindingRedisOpId must not be blank");
        }
    }
}
