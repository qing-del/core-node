package com.jacolp.document.application.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentRelationDO;
import com.jacolp.document.infrastructure.persistence.dataobject.ResourceNodeDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentRelationMapper;
import com.jacolp.document.infrastructure.persistence.mapper.ResourceNodeMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingBinding;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingBinding;
import com.jacolp.document.websocket.protocol.DocumentBindingCommandType;
import com.jacolp.document.websocket.protocol.DocumentBindingEnvelope;
import com.jacolp.document.websocket.protocol.DocumentBindingTargetType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class DocumentBindingConsumerTest {

    private static final UUID REF_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID OTHER_REF_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

    @Test
    void createsNodeAndRelationBeforeDeletingCommittedBindingEntries() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        ResourceNodeMapper resourceNodeMapper = mock(ResourceNodeMapper.class);
        DocumentRelationMapper relationMapper = mock(DocumentRelationMapper.class);
        TransactionTemplate transactionTemplate = transactionTemplateThatExecutesCallbacks();
        when(redisRepository.readPendingBindings(7L, 500)).thenReturn(List.of(
                pending("100-0", bind(REF_ID, 42L))), List.of());
        doAnswer(invocation -> {
            ResourceNodeDO node = invocation.getArgument(0);
            node.setId(900L);
            return 1;
        }).when(resourceNodeMapper).insert(any(ResourceNodeDO.class));
        when(relationMapper.insert(any())).thenReturn(1);
        when(redisRepository.deletePendingBindings(7L, List.of("100-0"))).thenReturn(1L);

        DocumentBindingConsumeResult result = consumer(redisRepository, resourceNodeMapper, relationMapper,
                transactionTemplate).drain(7L);

        ArgumentCaptor<ResourceNodeDO> node = ArgumentCaptor.forClass(ResourceNodeDO.class);
        verify(resourceNodeMapper).insert(node.capture());
        assertThat(node.getValue().getResourceType()).isEqualTo("DOCUMENT");
        assertThat(node.getValue().getTargetId()).isEqualTo(42L);
        ArgumentCaptor<DocumentRelationDO> relation = ArgumentCaptor.forClass(DocumentRelationDO.class);
        verify(relationMapper).insert(relation.capture());
        assertThat(relation.getValue()).isEqualTo(new DocumentRelationDO(7L, REF_ID.toString(), 900L, false));
        InOrder order = inOrder(transactionTemplate, redisRepository);
        order.verify(transactionTemplate).execute(any(TransactionCallback.class));
        order.verify(redisRepository).deletePendingBindings(7L, List.of("100-0"));
        assertThat(result).isEqualTo(new DocumentBindingConsumeResult(7L, 1, 1L));
    }

    @Test
    void reusesNodeAndOverwritesTargetWhenTheRefIdAlreadyExists() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        ResourceNodeMapper resourceNodeMapper = mock(ResourceNodeMapper.class);
        DocumentRelationMapper relationMapper = mock(DocumentRelationMapper.class);
        when(redisRepository.readPendingBindings(7L, 500)).thenReturn(List.of(
                pending("101-0", bind(REF_ID, 99L))), List.of());
        when(relationMapper.selectBySourceDocumentIdAndRefId(7L, REF_ID.toString()))
                .thenReturn(new DocumentRelationDO(7L, REF_ID.toString(), 900L, true));
        when(resourceNodeMapper.updateTargetById(900L, "DOCUMENT", 99L)).thenReturn(1);
        when(relationMapper.activateBySourceDocumentIdAndRefId(7L, REF_ID.toString(), 900L)).thenReturn(1);
        when(redisRepository.deletePendingBindings(7L, List.of("101-0"))).thenReturn(1L);

        consumer(redisRepository, resourceNodeMapper, relationMapper, transactionTemplateThatExecutesCallbacks())
                .drain(7L);

        verify(resourceNodeMapper, never()).insert(any(ResourceNodeDO.class));
        verify(resourceNodeMapper).updateTargetById(900L, "DOCUMENT", 99L);
        verify(relationMapper).activateBySourceDocumentIdAndRefId(7L, REF_ID.toString(), 900L);
    }

    @Test
    void softDeletesActiveRelationButDoesNotCreateTombstoneForMissingOrDeletedRelation() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        ResourceNodeMapper resourceNodeMapper = mock(ResourceNodeMapper.class);
        DocumentRelationMapper relationMapper = mock(DocumentRelationMapper.class);
        when(redisRepository.readPendingBindings(7L, 500)).thenReturn(List.of(
                pending("102-0", unbind(REF_ID, 42L)), pending("103-0", unbind(OTHER_REF_ID, 42L))), List.of());
        when(relationMapper.selectBySourceDocumentIdAndRefId(7L, REF_ID.toString()))
                .thenReturn(new DocumentRelationDO(7L, REF_ID.toString(), 900L, false));
        when(relationMapper.selectBySourceDocumentIdAndRefId(7L, OTHER_REF_ID.toString())).thenReturn(null);
        when(relationMapper.softDeleteBySourceDocumentIdAndRefId(7L, REF_ID.toString())).thenReturn(1);
        when(redisRepository.deletePendingBindings(7L, List.of("102-0", "103-0"))).thenReturn(2L);

        consumer(redisRepository, resourceNodeMapper, relationMapper, transactionTemplateThatExecutesCallbacks())
                .drain(7L);

        verify(relationMapper).softDeleteBySourceDocumentIdAndRefId(7L, REF_ID.toString());
        verify(relationMapper, never()).softDeleteBySourceDocumentIdAndRefId(7L, OTHER_REF_ID.toString());
        verify(resourceNodeMapper, never()).insert(any(ResourceNodeDO.class));
    }

    @Test
    void foldsOnlyTheSameRefIdAndKeepsDifferentRefsIndependent() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        ResourceNodeMapper resourceNodeMapper = mock(ResourceNodeMapper.class);
        DocumentRelationMapper relationMapper = mock(DocumentRelationMapper.class);
        when(redisRepository.readPendingBindings(7L, 500)).thenReturn(List.of(
                pending("104-0", bind(REF_ID, 42L)),
                pending("105-0", unbind(REF_ID, 42L)),
                pending("106-0", bind(OTHER_REF_ID, 43L))), List.of());
        when(relationMapper.selectBySourceDocumentIdAndRefId(7L, REF_ID.toString())).thenReturn(null);
        when(relationMapper.selectBySourceDocumentIdAndRefId(7L, OTHER_REF_ID.toString())).thenReturn(null);
        doAnswer(invocation -> {
            ResourceNodeDO node = invocation.getArgument(0);
            node.setId(node.getTargetId() + 1_000L);
            return 1;
        }).when(resourceNodeMapper).insert(any(ResourceNodeDO.class));
        when(relationMapper.insert(any())).thenReturn(1);
        when(redisRepository.deletePendingBindings(7L, List.of("104-0", "105-0", "106-0"))).thenReturn(3L);

        consumer(redisRepository, resourceNodeMapper, relationMapper, transactionTemplateThatExecutesCallbacks())
                .drain(7L);

        // REF_ID 的最后命令是 UNBIND，且此前没有关系，因此不创建墓碑；OTHER_REF_ID 独立创建关系。
        verify(resourceNodeMapper).insert(any(ResourceNodeDO.class));
        verify(relationMapper).insert(any(DocumentRelationDO.class));
        verify(relationMapper, never()).softDeleteBySourceDocumentIdAndRefId(anyLong(), anyString());
    }

    @Test
    void keepsBindingEntriesWhenProjectionTransactionFails() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        ResourceNodeMapper resourceNodeMapper = mock(ResourceNodeMapper.class);
        DocumentRelationMapper relationMapper = mock(DocumentRelationMapper.class);
        when(redisRepository.readPendingBindings(7L, 500)).thenReturn(List.of(
                pending("107-0", bind(REF_ID, 42L))));
        doThrow(new IllegalStateException("database unavailable"))
                .when(resourceNodeMapper).insert(any(ResourceNodeDO.class));

        assertThatThrownBy(() -> consumer(redisRepository, resourceNodeMapper, relationMapper,
                transactionTemplateThatExecutesCallbacks()).drain(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(redisRepository, never()).deletePendingBindings(anyLong(), any());
    }

    private static DocumentBindingConsumer consumer(DocumentRedisRepository redisRepository,
                                                    ResourceNodeMapper resourceNodeMapper,
                                                    DocumentRelationMapper relationMapper,
                                                    TransactionTemplate transactionTemplate) {
        return new DocumentBindingConsumer(redisRepository, resourceNodeMapper, relationMapper,
                transactionTemplate, new DocumentProperties());
    }

    private static TransactionTemplate transactionTemplateThatExecutesCallbacks() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(template).execute(any(TransactionCallback.class));
        return template;
    }

    private static StoredDocumentPendingBinding pending(String redisOpId, DocumentBindingEnvelope envelope) {
        return new StoredDocumentPendingBinding(redisOpId,
                new DocumentPendingBinding(7L, envelope.encodeBody()));
    }

    private static DocumentBindingEnvelope bind(UUID refId, long targetId) {
        return new DocumentBindingEnvelope(1, DocumentBindingCommandType.BIND, refId,
                DocumentBindingTargetType.DOCUMENT, targetId);
    }

    private static DocumentBindingEnvelope unbind(UUID refId, long targetId) {
        return new DocumentBindingEnvelope(1, DocumentBindingCommandType.UNBIND, refId,
                DocumentBindingTargetType.DOCUMENT, targetId);
    }
}
