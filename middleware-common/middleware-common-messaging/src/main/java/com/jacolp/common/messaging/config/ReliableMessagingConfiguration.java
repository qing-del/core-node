package com.jacolp.common.messaging.config;

import com.jacolp.common.messaging.constant.EventTopology;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReliableMessagingProperties.class)
public class ReliableMessagingConfiguration {

    @Bean
    Declarables domainEventTopology(ReliableMessagingProperties properties) {
        TopicExchange exchange = new TopicExchange(EventTopology.EXCHANGE, true, false);
        List<Declarable> declarations = new ArrayList<>();
        declarations.add(exchange);
        // 笔记模块队列：消费审核结果事件（audit.reviewed），异步应用过审/拒绝到笔记、标签及关联关系
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.NOTE_QUEUE,
                "audit.reviewed");
        // 媒体模块队列：消费审核结果事件（audit.reviewed），异步应用过审/拒绝到图片
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.MEDIA_QUEUE,
                "audit.reviewed");
        // 系统模块队列：用户存储额释放（storage.released）
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.SYSTEM_QUEUE, "storage.released");
        // 系统模块队列：管理员发送邮件（email.send-requested）
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.EMAIL_QUEUE, "email.send-requested");
        // 媒体数据-图片 删除异步任务队列，保证最终一致性
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.MEDIA_DELETE_QUEUE,
                "media.resource.delete-requested");
        // 审核模块投影队列：用户资料变更（user.profile-changed），维护审核列表展示快照
        addQueue(declarations, exchange, properties.getRetryQueueDelayMs(), EventTopology.AUDIT_PROJECTION_QUEUE,
                "user.profile-changed");
        return new Declarables(declarations);
    }

    /** 每个主队列自动配套 <queue>.retry 重试队列（TTL 到期回到主队列）与 <queue>.dlq 死信队列。 */
    private static void addQueue(List<Declarable> declarations, TopicExchange exchange,
                                 long retryDelayMs, String queueName, String... routingKeys) {
        Queue main = QueueBuilder.durable(queueName)
                .deadLetterExchange("")
                .deadLetterRoutingKey(EventTopology.deadLetterQueue(queueName))
                .build();
        Queue retry = QueueBuilder.durable(EventTopology.retryQueue(queueName))
                .deadLetterExchange("")
                .deadLetterRoutingKey(queueName)
                .ttl(Math.toIntExact(retryDelayMs))
                .build();
        Queue dead = QueueBuilder.durable(EventTopology.deadLetterQueue(queueName)).build();
        declarations.add(main);
        declarations.add(retry);
        declarations.add(dead);
        for (String routingKey : routingKeys) {
            declarations.add(BindingBuilder.bind(main).to(exchange).with(routingKey));
        }
    }
}
