package com.jacolp.document.application.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.jacolp.document.application.compact.DocumentSnapshotStorage;
import com.jacolp.document.application.yjs.YjsMergeClient;
import com.jacolp.document.application.yjs.YjsNodeIdentityMigrationResult;
import com.jacolp.document.application.yjs.YjsResourceReferenceRemap;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingUpdate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentNodeIdentityMigrationServiceTest {

    @Test
    void mergesDurableAndPendingUpdatesThenCasWritesSnapshotWithoutDeletingPending() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentSnapshotStorage snapshotStorage = mock(DocumentSnapshotStorage.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        YjsMergeClient yjsMergeClient = mock(YjsMergeClient.class);
        DocumentNodeIdentityMigrationPersistence migrationPersistence = mock(
                DocumentNodeIdentityMigrationPersistence.class);
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L));
        when(redisRepository.countPresence(7L)).thenReturn(0L);
        when(opLogMapper.selectByDocumentIdAfterId(7L, 5L, 500)).thenReturn(List.of(opLog(6L)))
                .thenReturn(List.of());
        when(redisRepository.readPendingUpdates(7L, Integer.MAX_VALUE)).thenReturn(List.of(pendingUpdate("1-0")));
        when(yjsMergeClient.migrateNodeIdentity(any(), anyList()))
                .thenReturn(new YjsNodeIdentityMigrationResult(new byte[] {9, 8}, true, 2))
                .thenReturn(new YjsNodeIdentityMigrationResult(new byte[] {9, 8, 7}, false, 2));
        when(snapshotStorage.write(eq(7L), any(byte[].class))).thenReturn("document/7/state/migrated.bin");
        when(migrationPersistence.replaceSnapshotAndCloneRelations(7L, 5L, "document/7/state/migrated.bin", 6L,
                List.of())).thenReturn(true);

        DocumentNodeIdentityMigrationOutcome outcome = new DocumentNodeIdentityMigrationService(documentMapper,
                opLogMapper, snapshotStorage, redisRepository, yjsMergeClient, new DocumentProperties(),
                migrationPersistence)
                .migrateDocument(7L);

        assertThat(outcome.status()).isEqualTo(DocumentNodeIdentityMigrationOutcome.Status.MIGRATED);
        assertThat(outcome.cutoffLogId()).isEqualTo(6L);
        verify(yjsMergeClient).migrateNodeIdentity(isNull(), anyList());
        verify(yjsMergeClient).migrateNodeIdentity(any(byte[].class), anyList());
        verify(migrationPersistence).replaceSnapshotAndCloneRelations(7L, 5L,
                "document/7/state/migrated.bin", 6L, List.of());
        verify(opLogMapper).deleteByDocumentIdThroughId(7L, 6L);
        verify(redisRepository, never()).deletePendingUpdates(anyLong(), anyList());
    }

    @Test
    void retriesFromFreshDocumentAfterCasLoss() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentSnapshotStorage snapshotStorage = mock(DocumentSnapshotStorage.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        YjsMergeClient yjsMergeClient = mock(YjsMergeClient.class);
        DocumentNodeIdentityMigrationPersistence migrationPersistence = mock(
                DocumentNodeIdentityMigrationPersistence.class);
        DocumentProperties properties = new DocumentProperties();
        properties.getNodeMigration().setMaxCasRetries(2);
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L), document(7L, null, 5L));
        when(redisRepository.countPresence(7L)).thenReturn(0L);
        when(opLogMapper.selectByDocumentIdAfterId(7L, 5L, 500)).thenReturn(List.of(), List.of());
        when(redisRepository.readPendingUpdates(7L, Integer.MAX_VALUE)).thenReturn(List.of(), List.of());
        when(yjsMergeClient.migrateNodeIdentity(isNull(), anyList()))
                .thenReturn(new YjsNodeIdentityMigrationResult(new byte[] {1}, true, 1))
                .thenReturn(new YjsNodeIdentityMigrationResult(new byte[] {2}, true, 1));
        when(snapshotStorage.write(eq(7L), any(byte[].class)))
                .thenReturn("document/7/state/loser.bin", "document/7/state/winner.bin");
        when(migrationPersistence.replaceSnapshotAndCloneRelations(eq(7L), eq(5L), anyString(), eq(5L), eq(List.of())))
                .thenReturn(false, true);

        DocumentNodeIdentityMigrationOutcome outcome = new DocumentNodeIdentityMigrationService(documentMapper,
                opLogMapper, snapshotStorage, redisRepository, yjsMergeClient, properties, migrationPersistence)
                .migrateDocument(7L);

        assertThat(outcome.status()).isEqualTo(DocumentNodeIdentityMigrationOutcome.Status.MIGRATED);
        assertThat(outcome.objectKey()).isEqualTo("document/7/state/winner.bin");
        verify(yjsMergeClient, org.mockito.Mockito.times(2)).migrateNodeIdentity(isNull(), anyList());
        verify(snapshotStorage).delete("document/7/state/loser.bin");
        verify(opLogMapper).deleteByDocumentIdThroughId(7L, 5L);
    }

    @Test
    void skipsDocumentWithActivePresenceBeforeReadingState() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentNodeIdentityMigrationPersistence migrationPersistence = mock(
                DocumentNodeIdentityMigrationPersistence.class);
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L));
        when(redisRepository.countPresence(7L)).thenReturn(1L);

        DocumentNodeIdentityMigrationOutcome outcome = new DocumentNodeIdentityMigrationService(documentMapper,
                mock(DocumentOpLogMapper.class), mock(DocumentSnapshotStorage.class), redisRepository,
                mock(YjsMergeClient.class), new DocumentProperties(), migrationPersistence).migrateDocument(7L);

        assertThat(outcome.status()).isEqualTo(DocumentNodeIdentityMigrationOutcome.Status.SKIPPED_ACTIVE_SESSIONS);
        verify(redisRepository, never()).readPendingUpdates(anyLong(), anyInt());
    }

    @Test
    void passesDuplicateResourceReferenceRemapsToTransactionalPersistence() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentSnapshotStorage snapshotStorage = mock(DocumentSnapshotStorage.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        YjsMergeClient yjsMergeClient = mock(YjsMergeClient.class);
        DocumentNodeIdentityMigrationPersistence migrationPersistence = mock(
                DocumentNodeIdentityMigrationPersistence.class);
        YjsResourceReferenceRemap remap = new YjsResourceReferenceRemap(
                "123e4567-e89b-12d3-a456-426614174010", "123e4567-e89b-12d3-a456-426614174011");
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L));
        when(redisRepository.countPresence(7L)).thenReturn(0L);
        when(opLogMapper.selectByDocumentIdAfterId(7L, 5L, 500)).thenReturn(List.of());
        when(redisRepository.readPendingUpdates(7L, Integer.MAX_VALUE)).thenReturn(List.of());
        when(yjsMergeClient.migrateNodeIdentity(isNull(), anyList()))
                .thenReturn(new YjsNodeIdentityMigrationResult(new byte[] {9}, true, 1, List.of(remap)));
        when(snapshotStorage.write(7L, new byte[] {9})).thenReturn("document/7/state/migrated.bin");
        when(migrationPersistence.replaceSnapshotAndCloneRelations(7L, 5L, "document/7/state/migrated.bin", 5L,
                List.of(remap))).thenReturn(true);

        DocumentNodeIdentityMigrationOutcome outcome = new DocumentNodeIdentityMigrationService(documentMapper,
                opLogMapper, snapshotStorage, redisRepository, yjsMergeClient, new DocumentProperties(),
                migrationPersistence).migrateDocument(7L);

        assertThat(outcome.status()).isEqualTo(DocumentNodeIdentityMigrationOutcome.Status.MIGRATED);
        verify(migrationPersistence).replaceSnapshotAndCloneRelations(7L, 5L,
                "document/7/state/migrated.bin", 5L, List.of(remap));
    }

    @Test
    void discardsSnapshotWhenRelationCloneTransactionFails() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentSnapshotStorage snapshotStorage = mock(DocumentSnapshotStorage.class);
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        YjsMergeClient yjsMergeClient = mock(YjsMergeClient.class);
        DocumentNodeIdentityMigrationPersistence migrationPersistence = mock(
                DocumentNodeIdentityMigrationPersistence.class);
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L));
        when(redisRepository.countPresence(7L)).thenReturn(0L);
        when(opLogMapper.selectByDocumentIdAfterId(7L, 5L, 500)).thenReturn(List.of());
        when(redisRepository.readPendingUpdates(7L, Integer.MAX_VALUE)).thenReturn(List.of());
        when(yjsMergeClient.migrateNodeIdentity(isNull(), anyList()))
                .thenReturn(new YjsNodeIdentityMigrationResult(new byte[] {9}, true, 1));
        when(snapshotStorage.write(7L, new byte[] {9})).thenReturn("document/7/state/orphan.bin");
        when(migrationPersistence.replaceSnapshotAndCloneRelations(7L, 5L, "document/7/state/orphan.bin", 5L,
                List.of())).thenThrow(new DocumentNodeIdentityMigrationException("missing active relation"));

        DocumentNodeIdentityMigrationService service = new DocumentNodeIdentityMigrationService(documentMapper,
                opLogMapper, snapshotStorage, redisRepository, yjsMergeClient, new DocumentProperties(),
                migrationPersistence);

        assertThatThrownBy(() -> service.migrateDocument(7L))
                .isInstanceOf(DocumentNodeIdentityMigrationException.class);
        verify(snapshotStorage).delete("document/7/state/orphan.bin");
    }

    private static DocumentDO document(long id, String objectKey, long persistedLogId) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, 42L, "title", objectKey, persistedLogId, now, 42L, false, 0L, now, now);
    }

    private static DocumentOpLogDO opLog(long id) {
        return new DocumentOpLogDO(id, 7L, id + "-0", "123e4567-e89b-12d3-a456-426614174000",
                new byte[] {1, 2}, 42L, "user", LocalDateTime.now());
    }

    private static StoredDocumentPendingUpdate pendingUpdate(String redisOpId) {
        return new StoredDocumentPendingUpdate(redisOpId, new DocumentPendingUpdate(7L, new byte[] {3},
                "123e4567-e89b-12d3-a456-426614174001", 42L, "user", System.currentTimeMillis()));
    }

    private static <T> T isNull() {
        return org.mockito.ArgumentMatchers.isNull();
    }
}
