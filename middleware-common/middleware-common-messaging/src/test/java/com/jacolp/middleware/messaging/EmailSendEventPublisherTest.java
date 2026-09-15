package com.jacolp.middleware.messaging;

import java.util.List;

import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.event.EmailSendRequestedEvent;
import com.jacolp.middleware.messaging.pulisher.EmailSendEventPublisher;
import com.jacolp.middleware.messaging.pulisher.OutboxEventPublisher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailSendEventPublisherTest {

    @Test
    void createsAnIndependentEnvelopeForEachRecipient() {
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        EmailSendEventPublisher publisher = new EmailSendEventPublisher(outbox);
        EmailSendRequestedEvent first = new EmailSendRequestedEvent(
                "a@example.com", "subject", "body", "CUSTOM", "custom:1");
        EmailSendRequestedEvent second = new EmailSendRequestedEvent(
                "b@example.com", "subject", "body", "CUSTOM", "custom:2");

        assertThat(publisher.publish(List.of(first, second))).isEqualTo(2);

        verify(outbox).publish(eq(EventTypes.EMAIL_SEND_REQUESTED), eq(EventTypes.EMAIL_SEND_REQUESTED),
                eq("EMAIL"), eq("custom:1"), anyString(), eq(first));
        verify(outbox).publish(eq(EventTypes.EMAIL_SEND_REQUESTED), eq(EventTypes.EMAIL_SEND_REQUESTED),
                eq("EMAIL"), eq("custom:2"), anyString(), eq(second));
    }
}
