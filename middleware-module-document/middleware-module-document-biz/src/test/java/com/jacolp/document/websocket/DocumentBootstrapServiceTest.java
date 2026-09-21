package com.jacolp.document.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingUpdate;
import com.jacolp.document.websocket.protocol.DocumentWsCodec;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import com.jacolp.framework.minio.MinioBucketResolver;
import com.jacolp.framework.minio.MinioObjectStorage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class DocumentBootstrapServiceTest {

    @Test
    void readsSnapshotThroughSharedMinioStorageApi() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        MinioObjectStorage objectStorage = mock(MinioObjectStorage.class);
        MinioBucketResolver bucketResolver = mock(MinioBucketResolver.class);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        PlatformTransactionManager transactionManager = transactionManager();
        WebSocketSession session = mock(WebSocketSession.class);
        byte[] snapshot = new byte[] {9, 8, 7};
        String objectKey = "document/7/state/snapshot.bin";
        when(bucketResolver.requireBucket("document")).thenReturn("middleware-document");
        when(objectStorage.read("middleware-document", objectKey, properties.getSnapshot().getMaxBytes()))
                .thenReturn(snapshot);
        DocumentDO document = new DocumentDO(7L, 42L, "title", objectKey, 0L,
                LocalDateTime.now(), 42L, false, 0L, LocalDateTime.now(), LocalDateTime.now());
        when(documentMapper.selectActiveById(7L)).thenReturn(document);
        when(opLogMapper.selectByDocumentIdAfterId(7L, 0L, properties.getFlushLog().getBatchSize()))
                .thenReturn(List.of());
        when(redisRepository.readPendingUpdates(7L, Integer.MAX_VALUE)).thenReturn(List.of());

        DocumentBootstrapService service = new DocumentBootstrapService(objectStorage, bucketResolver,
                documentMapper, opLogMapper, redisRepository, transactionManager, codec, properties);

        service.sendBootstrap(7L, session);

        ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.forClass(WebSocketMessage.class);
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        InOrder readAndSendOrder = inOrder(transactionManager, documentMapper, opLogMapper, objectStorage);
        readAndSendOrder.verify(transactionManager).getTransaction(definition.capture());
        readAndSendOrder.verify(documentMapper).selectActiveById(7L);
        readAndSendOrder.verify(opLogMapper).selectByDocumentIdAfterId(7L, 0L,
                properties.getFlushLog().getBatchSize());
        readAndSendOrder.verify(transactionManager).commit(any(TransactionStatus.class));
        readAndSendOrder.verify(objectStorage).read("middleware-document", objectKey,
                properties.getSnapshot().getMaxBytes());
        assertThat(definition.getValue().getIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThat(definition.getValue().isReadOnly()).isTrue();
        verify(session).sendMessage(messages.capture());
        assertThat(codec.decodeBinary((BinaryMessage) messages.getValue()).type())
                .isEqualTo(DocumentWsFrameType.SNAPSHOT_STATE);
        assertThat(codec.decodeBinary((BinaryMessage) messages.getValue()).payload()).containsExactly(snapshot);
    }

    @Test
    void sendsDurableAndRedisPendingUpdatesAsOpaqueBootstrapFrames() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        PlatformTransactionManager transactionManager = transactionManager();
        WebSocketSession session = mock(WebSocketSession.class);

        byte[] durableUpdate = new byte[] {1, 2, -1};
        byte[] pendingUpdate = new byte[] {3, 4, -2};
        DocumentDO document = new DocumentDO(7L, 42L, "title", null, 0L,
                LocalDateTime.now(), 42L, false, 0L, LocalDateTime.now(), LocalDateTime.now());
        when(documentMapper.selectActiveById(7L)).thenReturn(document);
        when(opLogMapper.selectByDocumentIdAfterId(7L, 0L, properties.getFlushLog().getBatchSize()))
                .thenReturn(List.of(new DocumentOpLogDO(1L, 7L, "1-0", "123e4567-e89b-12d3-a456-426614174000",
                        durableUpdate, 42L, "user", LocalDateTime.now())));
        when(redisRepository.readPendingUpdates(7L, Integer.MAX_VALUE)).thenReturn(List.of(
                new StoredDocumentPendingUpdate("2-0", new DocumentPendingUpdate(7L, pendingUpdate,
                        "123e4567-e89b-12d3-a456-426614174001", 42L, "user", 1L))));

        DocumentBootstrapService service = new DocumentBootstrapService(mock(MinioObjectStorage.class),
                mock(MinioBucketResolver.class),
                documentMapper, opLogMapper, redisRepository, transactionManager, codec, properties);

        service.sendBootstrap(7L, session);

        ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(2)).sendMessage(messages.capture());
        List<WebSocketMessage<?>> sent = messages.getAllValues();
        assertThat(sent).hasSize(2).allMatch(BinaryMessage.class::isInstance);
        assertThat(codec.decodeBinary((BinaryMessage) sent.get(0)).type()).isEqualTo(DocumentWsFrameType.BOOTSTRAP_UPDATE);
        assertThat(codec.decodeBinary((BinaryMessage) sent.get(0)).payload()).containsExactly(durableUpdate);
        assertThat(codec.decodeBinary((BinaryMessage) sent.get(1)).type()).isEqualTo(DocumentWsFrameType.BOOTSTRAP_UPDATE);
        assertThat(codec.decodeBinary((BinaryMessage) sent.get(1)).payload()).containsExactly(pendingUpdate);
    }

    @Test
    void readsRedisPendingBeforeBootstrapMetadata() {
        DocumentProperties properties = new DocumentProperties();
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        PlatformTransactionManager transactionManager = transactionManager();
        WebSocketSession session = mock(WebSocketSession.class);
        DocumentDO document = new DocumentDO(7L, 42L, "title", null, 0L,
                LocalDateTime.now(), 42L, false, 0L, LocalDateTime.now(), LocalDateTime.now());

        when(redisRepository.readPendingUpdates(7L, Integer.MAX_VALUE)).thenReturn(List.of());
        when(documentMapper.selectActiveById(7L)).thenReturn(document);
        when(opLogMapper.selectByDocumentIdAfterId(7L, 0L, properties.getFlushLog().getBatchSize()))
                .thenReturn(List.of());

        DocumentBootstrapService service = new DocumentBootstrapService(mock(MinioObjectStorage.class),
                mock(MinioBucketResolver.class), documentMapper, opLogMapper, redisRepository,
                transactionManager, codec, properties);

        service.sendBootstrap(7L, session);

        InOrder order = inOrder(redisRepository, transactionManager, documentMapper);
        order.verify(redisRepository).readPendingUpdates(7L, Integer.MAX_VALUE);
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        order.verify(documentMapper).selectActiveById(7L);
    }

    @Test
    void readsAllDurablePagesBeforeCommittingTheSingleReadView() throws Exception {
        DocumentProperties properties = new DocumentProperties();
        properties.getFlushLog().setBatchSize(1);
        DocumentWsCodec codec = new DocumentWsCodec(new ObjectMapper(), properties);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        PlatformTransactionManager transactionManager = transactionManager();
        WebSocketSession session = mock(WebSocketSession.class);
        DocumentDO document = new DocumentDO(7L, 42L, "title", null, 0L,
                LocalDateTime.now(), 42L, false, 0L, LocalDateTime.now(), LocalDateTime.now());

        when(redisRepository.readPendingUpdates(7L, Integer.MAX_VALUE)).thenReturn(List.of());
        when(documentMapper.selectActiveById(7L)).thenReturn(document);
        when(opLogMapper.selectByDocumentIdAfterId(7L, 0L, 1)).thenReturn(List.of(
                new DocumentOpLogDO(1L, 7L, "1-0", "123e4567-e89b-12d3-a456-426614174000",
                        new byte[] {1}, 42L, "user", LocalDateTime.now())));
        when(opLogMapper.selectByDocumentIdAfterId(7L, 1L, 1)).thenReturn(List.of());

        DocumentBootstrapService service = new DocumentBootstrapService(mock(MinioObjectStorage.class),
                mock(MinioBucketResolver.class), documentMapper, opLogMapper, redisRepository,
                transactionManager, codec, properties);

        service.sendBootstrap(7L, session);

        InOrder readOrder = inOrder(transactionManager, documentMapper, opLogMapper);
        readOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        readOrder.verify(documentMapper).selectActiveById(7L);
        readOrder.verify(opLogMapper).selectByDocumentIdAfterId(7L, 0L, 1);
        readOrder.verify(opLogMapper).selectByDocumentIdAfterId(7L, 1L, 1);
        readOrder.verify(transactionManager).commit(any(TransactionStatus.class));
        verify(transactionManager, times(1)).getTransaction(any(TransactionDefinition.class));
        verify(transactionManager, times(1)).commit(any(TransactionStatus.class));
        verify(opLogMapper).selectByDocumentIdAfterId(7L, 0L, 1);
        verify(opLogMapper).selectByDocumentIdAfterId(7L, 1L, 1);
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        return transactionManager;
    }
}
