package com.jacolp.middleware.messaging.base;

import java.time.LocalDateTime;

/** 出站事件表中的一行记录，由 {@link OutboxRelay} 领取后发布到 RabbitMQ。 */
public record OutboxRecord(
        long id,
        String eventId,
        String eventType,
        String routingKey,
        String payload,
        int retryCount,
        LocalDateTime createTime) {
}
