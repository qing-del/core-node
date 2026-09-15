package com.jacolp.middleware.messaging.service;

import java.util.Objects;
import java.util.function.Consumer;

import com.jacolp.middleware.messaging.base.EventEnvelope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes the business mutation and records successful consumption in one transaction. */
@Service
public class InboxService {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public InboxService(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 消费事件
     * @param envelope 事件
     * @param consumerName 消费者名称
     * @param handler 事件处理器
     * @return 是否消费成功
     */
    public boolean consume(EventEnvelope envelope, String consumerName, Consumer<EventEnvelope> handler) {
        checkParamNotNull(envelope, consumerName, handler); // 检查参数是否为空
        Boolean consumed = transactionTemplate.execute(status -> {
            try {
                // 更新任务状态
                jdbcTemplate.update("""
                        INSERT INTO sys_event_inbox
                            (event_id, consumer_name, event_type, status, create_time)
                        VALUES (?, ?, ?, 'PROCESSING', NOW())
                        """, envelope.eventId(), consumerName, envelope.eventType());
            } catch (DuplicateKeyException duplicate) {
                return false;
            }
            handler.accept(envelope);

            // 将任务状态标记为已成功
            int updated = jdbcTemplate.update("""
                    UPDATE sys_event_inbox
                    SET 
                        status = 'SUCCEEDED', 
                        consumed_time = NOW(), 
                        last_error = NULL
                    WHERE event_id = ? 
                        AND consumer_name = ? 
                        AND status = 'PROCESSING'
                    """, envelope.eventId(), consumerName);
            if (updated != 1) {
                throw new IllegalStateException("Unable to mark inbox event as consumed");
            }
            return true;
        });
        return Boolean.TRUE.equals(consumed);
    }

    /**
     * 检查参数是否为空
     * @param envelope 事件
     * @param consumerName 消费者名称
     * @param handler 事件处理器
     */
    private static void checkParamNotNull(EventEnvelope envelope, String consumerName, Consumer<EventEnvelope> handler) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
        Objects.requireNonNull(handler, "handler must not be null");
    }
}
