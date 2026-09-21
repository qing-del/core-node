package com.jacolp.document.infrastructure.redis;

/** 单个文档 Room 的 Redis 运行时元数据，始终不保存文档正文。 */
public record DocumentRoomMeta(
        /** Room 对应的文档 ID。<p>example: {@code 42}</p> */
        long documentId,
        /** 文档所有者用户 ID，仅用于运行态生命周期和审计。<p>example: {@code 10001}</p> */
        long ownerUserId,
        /** 是否已请求延迟关闭 Room。<p>example: {@code true}</p> */
        boolean closeRequested,
        /** CLOSE 请求的幂等令牌；未请求关闭时为空。<p>example: {@code 550e8400-e29b-41d4-a716-446655440000}</p> */
        String closeToken,
        /** Room 中最近一次接受更新的时间戳，单位为 Unix 毫秒。<p>example: {@code 1756080000000}</p> */
        long lastModifyTime,
        /** 最近修改用户 ID；没有用户修改时为空。<p>example: {@code 10001}</p> */
        Long lastModifyUserId) {

    public DocumentRoomMeta {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        if (ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        if (lastModifyTime < 0) {
            throw new IllegalArgumentException("lastModifyTime must not be negative");
        }
        if (closeToken != null && closeToken.isBlank()) {
            throw new IllegalArgumentException("closeToken must be nonblank when present");
        }
        if (closeRequested && closeToken == null) {
            throw new IllegalArgumentException("closeToken is required when close is requested");
        }
    }
}
