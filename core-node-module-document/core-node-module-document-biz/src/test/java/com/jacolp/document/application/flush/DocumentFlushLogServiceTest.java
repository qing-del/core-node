package com.jacolp.document.application.flush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingUpdate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class DocumentFlushLogServiceTest {

    @Test
    void persistsCutoffBeforeDeletingTheSameRedisEntries() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentOpLogMapper mapper = mock(DocumentOpLogMapper.class);
        TransactionTemplate transactionTemplate = transactionTemplateThatExecutesCallbacks();
        DocumentProperties properties = new DocumentProperties();
        when(redisRepository.readPendingUpdates(7L, 500)).thenReturn(List.of(
                pending("100-0", new byte[] {1, 2}), pending("101-0", new byte[] {3})));
        when(redisRepository.deletePendingUpdates(7L, List.of("100-0", "101-0"))).thenReturn(2L);

        DocumentFlushLogResult result = new DocumentFlushLogService(redisRepository, mapper, transactionTemplate, properties)
                .flush(7L);

        ArgumentCaptor<List<DocumentOpLogDO>> logs = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatchIgnoringDuplicates(logs.capture());
        assertThat(logs.getValue()).extracting(DocumentOpLogDO::getRedisOpId).containsExactly("100-0", "101-0");
        assertThat(logs.getValue()).extracting(DocumentOpLogDO::getUpdateData)
                .containsExactly(new byte[] {1, 2}, new byte[] {3});
        InOrder inOrder = inOrder(transactionTemplate, mapper, redisRepository);
        inOrder.verify(transactionTemplate).execute(any(TransactionCallback.class));
        inOrder.verify(mapper).insertBatchIgnoringDuplicates(any());
        inOrder.verify(redisRepository).deletePendingUpdates(7L, List.of("100-0", "101-0"));
        assertThat(result).isEqualTo(new DocumentFlushLogResult(7L, 2, 3L, 2L));
    }

    @Test
    void leavesRedisEntriesForIdempotentReplayWhenDatabaseWriteFails() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentOpLogMapper mapper = mock(DocumentOpLogMapper.class);
        TransactionTemplate transactionTemplate = transactionTemplateThatExecutesCallbacks();
        when(redisRepository.readPendingUpdates(eq(7L), any(Integer.class))).thenReturn(List.of(pending("100-0", new byte[] {1})));
        doThrow(new IllegalStateException("database unavailable")).when(mapper).insertBatchIgnoringDuplicates(any());

        DocumentFlushLogService service = new DocumentFlushLogService(redisRepository, mapper, transactionTemplate,
                new DocumentProperties());

        assertThatThrownBy(() -> service.flush(7L)).isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(redisRepository).readPendingUpdates(7L, 500);
        verify(redisRepository, org.mockito.Mockito.never()).deletePendingUpdates(any(Long.class), any());
    }

    @Test
    void limitsCutoffByConfiguredBinarySizeWithoutDroppingTheNextEntry() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentOpLogMapper mapper = mock(DocumentOpLogMapper.class);
        TransactionTemplate transactionTemplate = transactionTemplateThatExecutesCallbacks();
        DocumentProperties properties = new DocumentProperties();
        properties.getFlushLog().setMaxBatchBytes(3);
        when(redisRepository.readPendingUpdates(7L, 500)).thenReturn(List.of(
                pending("100-0", new byte[] {1, 2}), pending("101-0", new byte[] {3, 4})));
        when(redisRepository.deletePendingUpdates(7L, List.of("100-0"))).thenReturn(1L);

        DocumentFlushLogResult result = new DocumentFlushLogService(redisRepository, mapper, transactionTemplate, properties)
                .flush(7L);

        verify(redisRepository).deletePendingUpdates(7L, List.of("100-0"));
        assertThat(result).isEqualTo(new DocumentFlushLogResult(7L, 1, 2L, 1L));
    }

    private static TransactionTemplate transactionTemplateThatExecutesCallbacks() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(template).execute(any(TransactionCallback.class));
        return template;
    }

    private static StoredDocumentPendingUpdate pending(String redisOpId, byte[] update) {
        return new StoredDocumentPendingUpdate(redisOpId, new DocumentPendingUpdate(7L, update,
                "123e4567-e89b-12d3-a456-426614174000", 42L, "user", 1_723_000_000_000L));
    }
}
