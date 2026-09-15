package com.jacolp.middleware.messaging.pulisher;

import java.util.Objects;

import com.jacolp.middleware.messaging.constant.EventTopology;
import com.jacolp.middleware.messaging.config.ReliableMessagingProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 消费失败重试器：把失败消息转发到 <queue>.retry 重试队列（TTL 到期后重新回到主队列），
 * 超过最大重试次数后转发到 <queue>.dlq 死信队列；调用方应在该方法返回成功后才 ACK。
 */
@Component
public class EventRetryPublisher {
    public static final String RETRY_COUNT_HEADER = "x-application-retry-count";

    private final RabbitTemplate rabbitTemplate;
    private final ReliableMessagingProperties properties;

    public EventRetryPublisher(RabbitTemplate rabbitTemplate, ReliableMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    /** Republishes to a bounded retry queue; the caller may ACK only after this method returns. */
    public boolean retryOrDeadLetter(String mainQueue, Message original, RuntimeException failure) {
        Objects.requireNonNull(mainQueue, "mainQueue must not be null");
        int retries = retryCount(original) + 1;
        String destination = retries >= properties.getMaxRetries()
                ? EventTopology.deadLetterQueue(mainQueue)
                : EventTopology.retryQueue(mainQueue);
        Message copy = MessageBuilder.fromMessage(original)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader(RETRY_COUNT_HEADER, retries)
                .setHeader("x-last-error", safeError(failure))
                .build();
        rabbitTemplate.invoke(operations -> {
            operations.send("", destination, copy);
            operations.waitForConfirmsOrDie(properties.getConfirmTimeoutMs());
            return null;
        });
        return retries < properties.getMaxRetries();
    }

    private static int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_COUNT_HEADER);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String safeError(RuntimeException failure) {
        String value = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
