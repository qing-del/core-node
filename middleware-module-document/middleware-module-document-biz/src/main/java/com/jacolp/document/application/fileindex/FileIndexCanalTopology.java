package com.jacolp.document.application.fileindex;

import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.common.messaging.constant.EventTopology;
import java.util.Objects;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** document 专属 file 刷新队列及其 retry/DLQ 拓扑。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "jacolp.document.file-index", name = "enabled", havingValue = "true")
public class FileIndexCanalTopology {

    /** file 刷新事件使用独立直连交换机，避免与通用领域事件路由互相影响。 */
    public static final String EXCHANGE = "document.file-index.exchange";
    /** file 刷新事件路由键。 */
    public static final String ROUTING_KEY = "document.file-index.refresh";
    /** file 专属主队列。 */
    public static final String QUEUE = "document.file-index.queue";
    /** file 专属 retry 队列。 */
    public static final String RETRY_QUEUE = EventTopology.retryQueue(QUEUE);
    /** file 专属最终死信队列。 */
    public static final String DEAD_LETTER_QUEUE = EventTopology.deadLetterQueue(QUEUE);

    private final ReliableMessagingProperties messagingProperties;

    /** 保存现有可靠消息的确认超时/重试延迟配置。 */
    public FileIndexCanalTopology(ReliableMessagingProperties messagingProperties) {
        this.messagingProperties = Objects.requireNonNull(messagingProperties, "messagingProperties must not be null");
    }

    /** 声明 file 刷新专属持久化交换机。 */
    @Bean
    public DirectExchange fileIndexExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /** 声明主队列，并让未处理消息进入 document 专属 DLQ。 */
    @Bean
    public Queue fileIndexQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange("").deadLetterRoutingKey(DEAD_LETTER_QUEUE).build();
    }

    /** 声明复用现有消息延迟配置的 retry 队列。 */
    @Bean
    public Queue fileIndexRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE).deadLetterExchange("").deadLetterRoutingKey(QUEUE)
                .ttl(toQueueTtl(messagingProperties.getRetryQueueDelayMs())).build();
    }

    /** 声明 file 刷新最终死信队列。 */
    @Bean
    public Queue fileIndexDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /** 将 file 刷新路由键绑定到主队列。 */
    @Bean
    public Binding fileIndexBinding(@Qualifier("fileIndexQueue") Queue fileIndexQueue,
                                    @Qualifier("fileIndexExchange") DirectExchange fileIndexExchange) {
        return BindingBuilder.bind(fileIndexQueue).to(fileIndexExchange).with(ROUTING_KEY);
    }

    /** Rabbit TTL 需要正整数毫秒，沿用 document 调度拓扑的校验规则。 */
    private static int toQueueTtl(long delayMs) {
        if (delayMs <= 0 || delayMs > Integer.MAX_VALUE) {
            throw new IllegalStateException("jacolp.messaging.retry-queue-delay-ms must be between 1 and "
                    + Integer.MAX_VALUE);
        }
        return Math.toIntExact(delayMs);
    }
}
