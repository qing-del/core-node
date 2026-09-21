package com.jacolp.document.messaging;

import com.jacolp.document.api.model.DocumentScheduleType;
import java.util.Objects;

/** 不携带正文的小型调度信号，要求消费者重新检查文档状态。 */
public record DocumentScheduleMessage(
        /** 需要重新检查状态的文档 ID。<p>example: {@code 42}</p> */
        Long documentId,
        /** 调度动作类型。<p>example: {@code FLUSH_LOG}</p> */
        DocumentScheduleType type,
        /** 允许消费者执行动作的服务端时间戳，单位为 Unix 毫秒。<p>example: {@code 1756080000000}</p> */
        Long triggerTime,
        /** CLOSE 动作的幂等令牌；FLUSH_LOG/COMPACT 时为空。<p>example: {@code 550e8400-e29b-41d4-a716-446655440000}</p> */
        String closeToken) {

    /** 校验调度信号只携带可重读的文档标识和类型，不携带正文。 */
    public DocumentScheduleMessage {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(type, "type must not be null");
        if (triggerTime == null || triggerTime < 0) {
            throw new IllegalArgumentException("triggerTime must be non-negative");
        }
        if (type == DocumentScheduleType.CLOSE && (closeToken == null || closeToken.isBlank())) {
            throw new IllegalArgumentException("closeToken is required for CLOSE");
        }
    }
}
