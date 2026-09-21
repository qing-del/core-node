package com.jacolp.document.messaging;

import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.document.config.DocumentProperties;
import java.util.Objects;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 声明承载文档无正文调度信号的 RabbitMQ 拓扑。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentScheduleTopology {

    public static final String EXCHANGE = "document.schedule.exchange";
    public static final String ROUTING_KEY = "document.schedule";
    public static final String QUEUE = "document.schedule.queue";
    public static final String RETRY_QUEUE = QUEUE + ".retry";
    public static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";
    public static final String FLUSH_LOG_DELAY_QUEUE = "document.schedule.flush-log.delay";
    public static final String COMPACT_DELAY_QUEUE = "document.schedule.compact.delay";
    public static final String CLOSE_DELAY_QUEUE = "document.schedule.close.delay";

    private final DocumentProperties documentProperties;
    private final ReliableMessagingProperties messagingProperties;

    /** 创建使用文档时限和可靠消息配置声明拓扑的配置对象。 */
    public DocumentScheduleTopology(DocumentProperties documentProperties,
                                    ReliableMessagingProperties messagingProperties) {
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
        this.messagingProperties = Objects.requireNonNull(messagingProperties, "messagingProperties must not be null");
    }

    /** 声明持久化的文档调度直连交换机。 */
    @Bean
    public DirectExchange documentScheduleExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /** 声明主调度队列，并将无法处理的消息导向 DLQ。 */
    @Bean
    public Queue documentScheduleQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    /** 声明复用可靠消息延迟策略的重试队列。 */
    @Bean
    public Queue documentScheduleRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(QUEUE)
                .ttl(toQueueTtl(messagingProperties.getRetryQueueDelayMs(), "jacolp.messaging.retry-queue-delay-ms"))
                .build();
    }

    /** 声明最终无法处理的调度死信队列。 */
    @Bean
    public Queue documentScheduleDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /** 将交换机路由键绑定到主调度队列。 */
    @Bean
    public Binding documentScheduleBinding(@Qualifier("documentScheduleQueue") Queue documentScheduleQueue,
                                           @Qualifier("documentScheduleExchange") DirectExchange documentScheduleExchange) {
        return BindingBuilder.bind(documentScheduleQueue).to(documentScheduleExchange).with(ROUTING_KEY);
    }

    /** 声明 FLUSH_LOG 的固定 TTL 延迟队列，过期后回到主交换机。 */
    @Bean
    public Queue documentFlushLogDelayQueue() {
        return QueueBuilder.durable(FLUSH_LOG_DELAY_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .ttl(toQueueTtl(documentProperties.getFlushLog().getDelayMs(), "jacolp.document.flush-log.delay-ms"))
                .build();
    }

    /** 声明 COMPACT 的固定 TTL 延迟队列，过期后回到主交换机。 */
    @Bean
    public Queue documentCompactDelayQueue() {
        return QueueBuilder.durable(COMPACT_DELAY_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .ttl(toQueueTtl(documentProperties.getCompact().getIntervalMs(), "jacolp.document.compact.interval-ms"))
                .build();
    }

    /** 声明 CLOSE 的固定 TTL 延迟队列，过期后回到主交换机。 */
    @Bean
    public Queue documentCloseDelayQueue() {
        return QueueBuilder.durable(CLOSE_DELAY_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .ttl(toQueueTtl(documentProperties.getCloseDelayMs(), "jacolp.document.close-delay-ms"))
                .build();
    }

    /** 将 long 延迟转换为 RabbitMQ 要求的正整数 TTL。 */
    private static int toQueueTtl(long delayMs, String propertyName) {
        if (delayMs <= 0 || delayMs > Integer.MAX_VALUE) {
            throw new IllegalStateException(propertyName + " must be between 1 and " + Integer.MAX_VALUE);
        }
        return Math.toIntExact(delayMs);
    }
}
