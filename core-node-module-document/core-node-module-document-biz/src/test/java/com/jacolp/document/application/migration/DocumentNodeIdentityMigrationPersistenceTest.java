package com.jacolp.document.application.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.application.yjs.YjsResourceReferenceRemap;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentRelationMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentNodeIdentityMigrationPersistenceTest {

    @Test
    void advancesSnapshotAndClonesTheActiveRelationForEachRemap() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentRelationMapper relationMapper = mock(DocumentRelationMapper.class);
        YjsResourceReferenceRemap remap = new YjsResourceReferenceRemap(
                "123e4567-e89b-12d3-a456-426614174010", "123e4567-e89b-12d3-a456-426614174011");
        when(documentMapper.updateSnapshotPointerIfPersistedLogId(7L, 5L, "document/7/state/new.bin", 6L))
                .thenReturn(1);
        when(relationMapper.cloneActiveBySourceDocumentIdAndRefId(7L, remap.previousRefId(), remap.refId()))
                .thenReturn(1);

        boolean replaced = new DocumentNodeIdentityMigrationPersistence(documentMapper, relationMapper)
                .replaceSnapshotAndCloneRelations(7L, 5L, "document/7/state/new.bin", 6L, List.of(remap));

        assertThat(replaced).isTrue();
        verify(relationMapper).cloneActiveBySourceDocumentIdAndRefId(7L, remap.previousRefId(), remap.refId());
    }

    @Test
    void rejectsSnapshotAdvanceWhenTheOriginalRelationCannotBeCloned() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentRelationMapper relationMapper = mock(DocumentRelationMapper.class);
        YjsResourceReferenceRemap remap = new YjsResourceReferenceRemap(
                "123e4567-e89b-12d3-a456-426614174012", "123e4567-e89b-12d3-a456-426614174013");
        when(documentMapper.updateSnapshotPointerIfPersistedLogId(7L, 5L, "document/7/state/new.bin", 6L))
                .thenReturn(1);
        when(relationMapper.cloneActiveBySourceDocumentIdAndRefId(7L, remap.previousRefId(), remap.refId()))
                .thenReturn(0);

        assertThatThrownBy(() -> new DocumentNodeIdentityMigrationPersistence(documentMapper, relationMapper)
                .replaceSnapshotAndCloneRelations(7L, 5L, "document/7/state/new.bin", 6L, List.of(remap)))
                .isInstanceOf(DocumentNodeIdentityMigrationException.class);
    }
}
