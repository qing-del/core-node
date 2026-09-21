package com.jacolp.document.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import com.jacolp.document.api.model.DocumentScheduleType;
import com.jacolp.document.application.binding.DocumentBindingConsumer;
import com.jacolp.document.application.compact.DocumentCompactService;
import com.jacolp.document.application.close.DocumentCloseService;
import com.jacolp.document.application.flush.DocumentFlushLogResult;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import com.jacolp.document.config.DocumentProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;

class DocumentScheduleConsumerTest {

    @Test
    void flushesStateRecheckedFromRedisForFlushSchedule() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DocumentFlushLogService flushLogService = mock(DocumentFlushLogService.class);
        DocumentBindingConsumer bindingConsumer = mock(DocumentBindingConsumer.class);
        DocumentCompactService compactService = mock(DocumentCompactService.class);
        DocumentCloseService closeService = mock(DocumentCloseService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        EventRetryPublisher retryPublisher = mock(EventRetryPublisher.class);
        when(flushLogService.flush(7L)).thenReturn(new DocumentFlushLogResult(7L, 1, 1L, 1L));
        DocumentScheduleConsumer consumer = new DocumentScheduleConsumer(objectMapper, flushLogService, bindingConsumer,
                compactService,
                closeService, schedulePublisher, new DocumentProperties(), retryPublisher);
        Message message = new Message(objectMapper.writeValueAsBytes(new DocumentScheduleMessage(7L,
                DocumentScheduleType.FLUSH_LOG, 1_000L, null)));

        consumer.onMessage(message);

        InOrder order = inOrder(flushLogService, bindingConsumer, schedulePublisher);
        order.verify(flushLogService).flush(7L);
        order.verify(bindingConsumer).drain(7L);
        order.verify(schedulePublisher).scheduleCompact(7L);
    }

    @Test
    void schedulesImmediateCompactWhenJustFlushedBatchCrossesThreshold() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DocumentFlushLogService flushLogService = mock(DocumentFlushLogService.class);
        DocumentBindingConsumer bindingConsumer = mock(DocumentBindingConsumer.class);
        DocumentCompactService compactService = mock(DocumentCompactService.class);
        DocumentCloseService closeService = mock(DocumentCloseService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        EventRetryPublisher retryPublisher = mock(EventRetryPublisher.class);
        DocumentProperties properties = new DocumentProperties();
        properties.getCompact().setMaxUnmergedOps(2);
        when(flushLogService.flush(7L)).thenReturn(new DocumentFlushLogResult(7L, 2, 1L, 2L));
        DocumentScheduleConsumer consumer = new DocumentScheduleConsumer(objectMapper, flushLogService, bindingConsumer,
                compactService,
                closeService, schedulePublisher, properties, retryPublisher);

        consumer.onMessage(new Message(objectMapper.writeValueAsBytes(new DocumentScheduleMessage(7L,
                DocumentScheduleType.FLUSH_LOG, 1_000L, null))));

        verify(schedulePublisher).scheduleCompactImmediately(7L);
    }

    @Test
    void runsCompactionForCompactSchedule() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DocumentCompactService compactService = mock(DocumentCompactService.class);
        DocumentCloseService closeService = mock(DocumentCloseService.class);
        DocumentScheduleConsumer consumer = new DocumentScheduleConsumer(objectMapper, mock(DocumentFlushLogService.class),
                mock(DocumentBindingConsumer.class), compactService, closeService, mock(DocumentSchedulePublisher.class),
                new DocumentProperties(),
                mock(EventRetryPublisher.class));

        consumer.onMessage(new Message(objectMapper.writeValueAsBytes(new DocumentScheduleMessage(7L,
                DocumentScheduleType.COMPACT, 1_000L, null))));

        verify(compactService).compact(7L);
    }

    @Test
    void runsFinalCloseForCloseSchedule() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DocumentCloseService closeService = mock(DocumentCloseService.class);
        DocumentScheduleConsumer consumer = new DocumentScheduleConsumer(objectMapper, mock(DocumentFlushLogService.class),
                mock(DocumentBindingConsumer.class), mock(DocumentCompactService.class), closeService,
                mock(DocumentSchedulePublisher.class),
                new DocumentProperties(), mock(EventRetryPublisher.class));

        consumer.onMessage(new Message(objectMapper.writeValueAsBytes(new DocumentScheduleMessage(7L,
                DocumentScheduleType.CLOSE, 1_000L, "close-token"))));

        verify(closeService).close(7L, "close-token");
    }

    @Test
    void sendsFailuresThroughExistingRetryAndDeadLetterMechanism() {
        DocumentFlushLogService flushLogService = mock(DocumentFlushLogService.class);
        DocumentBindingConsumer bindingConsumer = mock(DocumentBindingConsumer.class);
        DocumentCompactService compactService = mock(DocumentCompactService.class);
        DocumentCloseService closeService = mock(DocumentCloseService.class);
        DocumentSchedulePublisher schedulePublisher = mock(DocumentSchedulePublisher.class);
        EventRetryPublisher retryPublisher = mock(EventRetryPublisher.class);
        when(retryPublisher.retryOrDeadLetter(any(), any(), any())).thenReturn(true);
        DocumentScheduleConsumer consumer = new DocumentScheduleConsumer(new ObjectMapper(), flushLogService,
                bindingConsumer, compactService, closeService, schedulePublisher, new DocumentProperties(), retryPublisher);

        consumer.onMessage(new Message(new byte[] {'b', 'a', 'd'}));

        verify(retryPublisher).retryOrDeadLetter(org.mockito.Mockito.eq(DocumentScheduleTopology.QUEUE), any(), any());
    }
}
