package com.jacolp.document.application.compact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.framework.minio.MinioBucketResolver;
import com.jacolp.framework.minio.MinioObjectStorage;
import com.jacolp.framework.minio.MinioObjectSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentSnapshotStorageTest {

    @Test
    void listsOnlyHistoricalBinarySnapshotsForTheRequestedDocument() {
        MinioObjectStorage objectStorage = mock(MinioObjectStorage.class);
        MinioBucketResolver bucketResolver = mock(MinioBucketResolver.class);
        when(bucketResolver.requireBucket("document")).thenReturn("documents");
        when(objectStorage.listByPrefix(eq("documents"), eq("document/7/state/"))).thenReturn(List.of(
                new MinioObjectSummary("document/7/state/current.bin", 10L),
                new MinioObjectSummary("document/7/state/history.bin", 20L),
                new MinioObjectSummary("document/7/state/metadata.json", 30L),
                new MinioObjectSummary("document/8/state/other.bin", 40L)));
        DocumentSnapshotStorage storage = new DocumentSnapshotStorage(objectStorage, bucketResolver,
                mock(DocumentProperties.class));

        assertThat(storage.listHistorical(7L, "document/7/state/current.bin"))
                .containsExactly(new MinioObjectSummary("document/7/state/history.bin", 20L));
        verify(objectStorage).listByPrefix("documents", "document/7/state/");
    }
}
