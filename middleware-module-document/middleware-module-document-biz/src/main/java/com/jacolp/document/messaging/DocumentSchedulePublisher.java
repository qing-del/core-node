package com.jacolp.document.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.document.api.model.DocumentScheduleType;
import com.jacolp.document.config.DocumentProperties;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 发布不含正文的调度信号；文档状态仍保存在 Redis 与 MySQL。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentSchedulePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentProperties documentProperties;
    private final ReliableMessagingProperties messagingProperties;

    /** 创建使用 RabbitTemplate confirm 的文档调度发布器。 */
    public DocumentSchedulePublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                                     DocumentProperties documentProperties,
                                     ReliableMessagingProperties messagingProperties) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
        this.messagingProperties = Objects.requireNonNull(messagingProperties, "messagingProperties must not be null");
    }

    /** 发布带固定 TTL 的 FLUSH_LOG 去抖信号。 */
    public void scheduleFlushLog(long documentId) {
        Message message = newScheduleMessage(documentId, DocumentScheduleType.FLUSH_LOG,
                System.currentTimeMillis() + documentProperties.getFlushLog().getDelayMs(), null);
        publish(DocumentScheduleTopology.FLUSH_LOG_DELAY_QUEUE, message);
    }

    /** 发布延迟 COMPACT 信号，由消费者重新读取最新日志状态。 */
    public void scheduleCompact(long documentId) {
        Message message = newScheduleMessage(documentId, DocumentScheduleType.COMPACT,
                System.currentTimeMillis() + documentProperties.getCompact().getIntervalMs(), null);
        publish(DocumentScheduleTopology.COMPACT_DELAY_QUEUE, message);
    }

    /** 直接投递 COMPACT 信号，用于达到批次阈值后的立即尝试。 */
    public void scheduleCompactImmediately(long documentId) {
        Message message = newScheduleMessage(documentId, DocumentScheduleType.COMPACT, System.currentTimeMillis(), null);
        publish(DocumentScheduleTopology.QUEUE, message);
    }

    /** 发布带关闭令牌的延迟 CLOSE 信号。 */
    public void scheduleClose(long documentId, String closeToken) {
        Message message = newScheduleMessage(documentId, DocumentScheduleType.CLOSE,
                System.currentTimeMillis() + documentProperties.getCloseDelayMs(), closeToken);
        publish(DocumentScheduleTopology.CLOSE_DELAY_QUEUE, message);
    }

    /** 发送持久化消息并等待 Broker confirm，失败时向调用方抛出异常。 */
    private void publish(String queue, Message message) {
        rabbitTemplate.invoke(operations -> {
            operations.send("", queue, message);
            operations.waitForConfirmsOrDie(messagingProperties.getConfirmTimeoutMs());
            return null;
        });
    }

    /** 构造 JSON、持久化属性和唯一消息 ID 都已设置的 Rabbit 消息。 */
    Message newScheduleMessage(long documentId, DocumentScheduleType type, long triggerTime, String closeToken) {
        DocumentScheduleMessage schedule = new DocumentScheduleMessage(documentId, type, triggerTime, closeToken);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(serialize(schedule), properties);
    }

    /** 将轻量调度信号序列化为 UTF-8 JSON 字节。 */
    private byte[] serialize(DocumentScheduleMessage schedule) {
        try {
            return objectMapper.writeValueAsBytes(schedule);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize document scheduling signal", exception);
        }
    }
}
