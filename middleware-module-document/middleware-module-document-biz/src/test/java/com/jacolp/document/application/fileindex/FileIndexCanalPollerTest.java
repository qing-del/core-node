package com.jacolp.document.application.fileindex;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.Message;
import com.jacolp.document.config.FileIndexCanalProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FileIndexCanalPollerTest {

    @Test
    void acknowledgesOnlyAfterRabbitPublishSucceeds() {
        CanalConnector connector = mock(CanalConnector.class);
        Message batch = mock(Message.class);
        FileIndexCanalChangeExtractor extractor = mock(FileIndexCanalChangeExtractor.class);
        FileIndexCanalPublisher publisher = mock(FileIndexCanalPublisher.class);
        when(connector.get(100)).thenReturn(batch);
        when(batch.getId()).thenReturn(17L);
        when(batch.getEntries()).thenReturn(List.of(mock(Entry.class)));
        when(extractor.extract(batch)).thenReturn(List.of(new FileIndexResourceKey(FileIndexResourceType.NOTE, 7L)));
        FileIndexCanalPoller poller = new FileIndexCanalPoller(new FileIndexCanalProperties(), extractor, publisher,
                connector);
        poller.connect();

        poller.poll();

        InOrder order = inOrder(publisher, connector);
        order.verify(publisher).publish(any());
        order.verify(connector).ack(17L);
        verify(connector, never()).rollback(17L);
        poller.disconnect();
    }

    @Test
    void rollsBackCurrentBatchWhenRabbitPublishFails() {
        CanalConnector connector = mock(CanalConnector.class);
        Message batch = mock(Message.class);
        FileIndexCanalChangeExtractor extractor = mock(FileIndexCanalChangeExtractor.class);
        FileIndexCanalPublisher publisher = mock(FileIndexCanalPublisher.class);
        when(connector.get(100)).thenReturn(batch);
        when(batch.getId()).thenReturn(18L);
        when(batch.getEntries()).thenReturn(List.of(mock(Entry.class)));
        when(extractor.extract(batch)).thenReturn(List.of(new FileIndexResourceKey(FileIndexResourceType.IMAGE, 8L)));
        doThrow(new IllegalStateException("Rabbit unavailable")).when(publisher).publish(any());
        FileIndexCanalPoller poller = new FileIndexCanalPoller(new FileIndexCanalProperties(), extractor, publisher,
                connector);
        poller.connect();

        poller.poll();

        verify(connector).rollback(18L);
        verify(connector, never()).ack(18L);
        poller.disconnect();
    }
}
