package com.jacolp.middleware.messaging;

import java.time.Duration;

import com.jacolp.common.messaging.base.OutboxRelay;
import com.jacolp.common.messaging.base.OutboxRepository;
import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxRelayTest {
    @Test
    void exponentialBackoffIsBounded() {
        ReliableMessagingProperties properties = new ReliableMessagingProperties();
        properties.setInitialBackoff(Duration.ofSeconds(2));
        properties.setMaxBackoff(Duration.ofSeconds(30));
        OutboxRelay relay = new OutboxRelay(mock(OutboxRepository.class), mock(RabbitTemplate.class), properties);

        assertThat(relay.retryBackoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(relay.retryBackoff(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(relay.retryBackoff(20)).isEqualTo(Duration.ofSeconds(30));
    }
}
