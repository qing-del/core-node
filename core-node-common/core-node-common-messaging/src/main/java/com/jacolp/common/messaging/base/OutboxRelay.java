package com.jacolp.common.messaging.base;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.common.messaging.constant.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "jacolp.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ReliableMessagingProperties properties;
    private final String claimant = UUID.randomUUID().toString();   // 身份标识，用于防止其他实例出现 并发脏写 污染状态

    public OutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
                       ReliableMessagingProperties properties) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${jacolp.messaging.poll-delay-ms:1000}")
    public void relay() {
        List<OutboxRecord> records;
        try {
            records = repository.claimBatch(
                    claimant,
                    properties.getBatchSize(),
                    properties.getClaimSeconds()
            );
        } catch (RuntimeException e) {
            log.warn("Unable to claim outbox events: {}", e.getMessage());
            return;
        }
        records.forEach(this::publish);
    }

    void publish(OutboxRecord record) {
        try {
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            messageProperties.setMessageId(record.eventId());
            messageProperties.setHeader("eventId", record.eventId());
            messageProperties.setHeader("eventType", record.eventType());
            Message message = new Message(record.payload().getBytes(StandardCharsets.UTF_8), messageProperties);
            rabbitTemplate.invoke(operations -> {
                // 发送信息到指定的交换机和路由键，并等待确认
                operations.send(EventTopology.EXCHANGE, record.routingKey(), message);
                // 等待确认，需要等待信息真的进入了 Queue 才会返回，否则会抛出异常
                operations.waitForConfirmsOrDie(properties.getConfirmTimeoutMs());
                return null;
            });
            if (!repository.markPublished(record.id(), claimant)) {
                log.warn("Outbox publish confirmation lost ownership, eventId={}", record.eventId());
            }
        } catch (RuntimeException e) {
            int retryCount = record.retryCount() + 1;
            boolean dead = retryCount >= properties.getMaxRetries();
            Duration backoff = retryBackoff(retryCount);
            repository.markFailed(
                    record.id(),
                    claimant,
                    retryCount,
                    LocalDateTime.now().plus(backoff),
                    e.getClass().getSimpleName() + ": " + e.getMessage(),
                    dead
            );
            log.warn("Outbox publish failed, eventId={}, retryCount={}, dead={}",
                    record.eventId(), retryCount, dead);
        }
    }

    /**
     * 计算退避时间
     * @param retryCount 当前重试次数
     * @return 退避时间
     */
    public Duration retryBackoff(int retryCount) {
        // 计算退避指数的值，2^(retryCount - 1)
        long multiplier = 1L << Math.min(
                                        Math.max(retryCount - 1, 0),
                                        30
                                    );
        Duration candidate;
        try {
            // 使用 初始退避时间 x 指数值 计算退避时间
            candidate = properties.getInitialBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            // 如果出现溢出，则直接使用最大退避时间
            candidate = properties.getMaxBackoff();
        }

        // 如果退避时间超过最大退避时间，则直接使用最大退避时间
        return candidate.compareTo(properties.getMaxBackoff()) > 0 ?
                properties.getMaxBackoff() : candidate;
    }
}
