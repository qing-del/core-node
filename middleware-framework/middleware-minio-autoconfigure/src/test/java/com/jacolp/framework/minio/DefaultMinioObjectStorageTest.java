package com.jacolp.framework.minio;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DefaultMinioObjectStorageTest {

    @Test
    void createsMissingBucketBeforeWritingObject() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        new DefaultMinioObjectStorage(minioClient).write("documents", "state.bin", new byte[] {1},
                "application/octet-stream");

        InOrder order = inOrder(minioClient);
        order.verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        order.verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        order.verify(minioClient).putObject(any(PutObjectArgs.class));
        verifyNoMoreInteractions(minioClient);
    }

    @Test
    void doesNotCreateExistingBucketBeforeWritingObject() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        new DefaultMinioObjectStorage(minioClient).write("documents", "state.bin", new byte[] {1},
                "application/octet-stream");

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verifyNoMoreInteractions(minioClient);
    }

    @Test
    void listsObjectKeysAndSizesUnderTheRequestedPrefix() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        @SuppressWarnings("unchecked")
        Result<Item> result = mock(Result.class);
        Item item = mock(Item.class);
        when(result.get()).thenReturn(item);
        when(item.objectName()).thenReturn("document/7/state/history.bin");
        when(item.size()).thenReturn(42L);
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(result));

        List<MinioObjectSummary> objects = new DefaultMinioObjectStorage(minioClient)
                .listByPrefix("documents", "document/7/state/");

        assertThat(objects).containsExactly(new MinioObjectSummary("document/7/state/history.bin", 42L));
        verify(minioClient).listObjects(any(ListObjectsArgs.class));
    }
}
