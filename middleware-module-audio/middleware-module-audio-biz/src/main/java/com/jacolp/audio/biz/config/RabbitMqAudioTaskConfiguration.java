package com.jacolp.audio.biz.config;

import com.jacolp.audio.biz.service.RabbitMqTaskPublisher;
import com.jacolp.audio.biz.service.RabbitMqAudioResourceDeletePublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@ConditionalOnProperty(prefix = "jacolp.audio", name = "queue-type", havingValue = "rabbitmq")
public class RabbitMqAudioTaskConfiguration {
    public static final String QUEUE = "audio.generate.queue";
    public static final String DELETE_QUEUE = "audio.delete.queue";

    @Bean
    public DirectExchange audioGenerateExchange() {
        return new DirectExchange(RabbitMqTaskPublisher.EXCHANGE, true, false);
    }

    @Bean
    public Queue audioGenerateQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding audioGenerateBinding(@Qualifier("audioGenerateQueue") Queue audioGenerateQueue,
                                        @Qualifier("audioGenerateExchange") DirectExchange audioGenerateExchange) {
        return BindingBuilder.bind(audioGenerateQueue).to(audioGenerateExchange).with(RabbitMqTaskPublisher.ROUTING_KEY);
    }

    @Bean
    public DirectExchange audioDeleteExchange() {
        return new DirectExchange(RabbitMqAudioResourceDeletePublisher.EXCHANGE, true, false);
    }

    @Bean
    public Queue audioDeleteQueue() {
        return QueueBuilder.durable(DELETE_QUEUE).build();
    }

    @Bean
    public Binding audioDeleteBinding(@Qualifier("audioDeleteQueue") Queue audioDeleteQueue,
                                      @Qualifier("audioDeleteExchange") DirectExchange audioDeleteExchange) {
        return BindingBuilder.bind(audioDeleteQueue).to(audioDeleteExchange)
                .with(RabbitMqAudioResourceDeletePublisher.ROUTING_KEY);
    }
}
