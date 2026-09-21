package com.jacolp.document.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.document.config.DocumentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class DocumentScheduleTopologyTest {

    @Test
    void declaresFlushDelayQueueWithConfiguredTtlAndDeadLetterRoute() {
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.getFlushLog().setDelayMs(2_500L);
        DocumentScheduleTopology topology = new DocumentScheduleTopology(documentProperties,
                new ReliableMessagingProperties());

        Queue queue = topology.documentFlushLogDelayQueue();

        assertThat(queue.getName()).isEqualTo(DocumentScheduleTopology.FLUSH_LOG_DELAY_QUEUE);
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 2_500)
                .containsEntry("x-dead-letter-exchange", DocumentScheduleTopology.EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", DocumentScheduleTopology.ROUTING_KEY);
    }

    @Test
    void declaresCompactDelayQueueWithConfiguredTtlAndDeadLetterRoute() {
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.getCompact().setIntervalMs(20_000L);
        DocumentScheduleTopology topology = new DocumentScheduleTopology(documentProperties,
                new ReliableMessagingProperties());

        Queue queue = topology.documentCompactDelayQueue();

        assertThat(queue.getName()).isEqualTo(DocumentScheduleTopology.COMPACT_DELAY_QUEUE);
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 20_000)
                .containsEntry("x-dead-letter-exchange", DocumentScheduleTopology.EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", DocumentScheduleTopology.ROUTING_KEY);
    }

    @Test
    void declaresCloseDelayQueueWithConfiguredTtlAndDeadLetterRoute() {
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.setCloseDelayMs(30_000L);
        DocumentScheduleTopology topology = new DocumentScheduleTopology(documentProperties,
                new ReliableMessagingProperties());

        Queue queue = topology.documentCloseDelayQueue();

        assertThat(queue.getName()).isEqualTo(DocumentScheduleTopology.CLOSE_DELAY_QUEUE);
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 30_000)
                .containsEntry("x-dead-letter-exchange", DocumentScheduleTopology.EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", DocumentScheduleTopology.ROUTING_KEY);
    }
}
