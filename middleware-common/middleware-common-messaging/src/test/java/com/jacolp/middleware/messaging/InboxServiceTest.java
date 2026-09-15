package com.jacolp.middleware.messaging;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.jacolp.middleware.messaging.base.EventEnvelope;
import com.jacolp.middleware.messaging.service.InboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InboxServiceTest {
    private JdbcTemplate jdbcTemplate;
    private InboxService inboxService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        inboxService = new InboxService(jdbcTemplate, transactionTemplate);
    }

    @Test
    void duplicateDeliveryIsAcknowledgedWithoutRepeatingBusinessMutation() {
        EventEnvelope event = event();
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        AtomicInteger mutations = new AtomicInteger();

        boolean consumed = inboxService.consume(event, "test-consumer",
                ignored -> mutations.incrementAndGet());

        assertThat(consumed).isFalse();
        assertThat(mutations).hasValue(0);
    }

    @Test
    void businessFailurePropagatesBeforeSuccessCanBeRecorded() {
        EventEnvelope event = event();
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        assertThatThrownBy(() -> inboxService.consume(event, "test-consumer",
                ignored -> { throw new IllegalStateException("mutation failed"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mutation failed");
    }

    private static EventEnvelope event() {
        return new EventEnvelope("event-1", "sample", 1, "SAMPLE", "1",
                Instant.now(), null, JsonNodeFactory.instance.objectNode());
    }
}
