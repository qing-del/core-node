package com.jacolp.document.application.fileindex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 将 Canal batch 的资源定位键批量发布到 document 专属 Rabbit 队列，并等待 broker confirm。 */
@Component
public class FileIndexCanalPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ReliableMessagingProperties messagingProperties;

    /** 保存 Rabbit 发布确认和 JSON 编码依赖。 */
    public FileIndexCanalPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                                   ReliableMessagingProperties messagingProperties) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.messagingProperties = Objects.requireNonNull(messagingProperties, "messagingProperties must not be null");
    }

    /** 批量发布去重后的资源刷新消息；方法返回成功前不应 ACK 对应 Canal batch。 */
    public void publish(List<FileIndexResourceKey> resourceKeys) {
        Objects.requireNonNull(resourceKeys, "resourceKeys must not be null");
        List<FileIndexRefreshMessage> messages = new LinkedHashSet<>(resourceKeys).stream()
                .map(FileIndexRefreshMessage::forResource).toList();
        if (messages.isEmpty()) {
            return;
        }
        rabbitTemplate.invoke(operations -> {
            for (FileIndexRefreshMessage event : messages) {
                operations.send(FileIndexCanalTopology.EXCHANGE, FileIndexCanalTopology.ROUTING_KEY,
                        toMessage(event));
            }
            operations.waitForConfirmsOrDie(messagingProperties.getConfirmTimeoutMs());
            return null;
        });
    }

    /** 生成持久化 JSON 消息；body 只包含事件类型、资源类型、资源 ID 和 UUID。 */
    private Message toMessage(FileIndexRefreshMessage event) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(event.eventId());
            properties.setHeader("eventType", event.eventType());
            return new Message(objectMapper.writeValueAsBytes(event), properties);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("could not serialize file index refresh event", exception);
        }
    }
}
