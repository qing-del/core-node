package com.jacolp.middleware.module.media.biz.application.api;

import com.jacolp.module.media.api.command.MediaFileLookupCommand;
import com.jacolp.module.media.api.model.MediaReviewStatus;
import com.jacolp.module.media.biz.application.api.MediaFileApiService;
import com.jacolp.module.media.biz.application.api.MediaUsageApiService;
import com.jacolp.module.media.biz.infrastructure.persistence.dataobject.ImageDO;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaReadApiServiceTest {

    @Test
    void lookupDeduplicatesFilenamesAndPreservesUncategorizedTopic() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        ImageDO image = image(12L, null, (short) 2);
        when(imageMapper.selectByUserIdAndTopicIdAndFilenames(8L, null, List.of("cover.png")))
                .thenReturn(List.of(image));

        var summaries = new MediaFileApiService(imageMapper).findByOwnerTopicAndFilenames(
                new MediaFileLookupCommand(8L, null, List.of("cover.png", "cover.png")));

        assertEquals(12L, summaries.get("cover.png").id());
        assertEquals(null, summaries.get("cover.png").topicId());
        assertEquals(MediaReviewStatus.APPROVED, summaries.get("cover.png").status());
        assertTrue(summaries.get("cover.png").publiclyVisible());
        verify(imageMapper).selectByUserIdAndTopicIdAndFilenames(8L, null, List.of("cover.png"));
    }

    @Test
    void findByIdsDeduplicatesAndMapsReviewStatuses() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.selectByIds(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(List.of(
                image(1L, 11L, (short) 0), image(2L, 11L, (short) 1), image(3L, 11L, (short) 2),
                image(4L, 11L, (short) 3), image(5L, 11L, (short) 4)));

        var summaries = new MediaFileApiService(imageMapper).findByIds(List.of(1L, 2L, 2L, 3L, 4L, 5L));

        assertEquals(MediaReviewStatus.WAITING, summaries.get(1L).status());
        assertEquals(MediaReviewStatus.REVIEWING, summaries.get(2L).status());
        assertEquals(MediaReviewStatus.APPROVED, summaries.get(3L).status());
        assertEquals(MediaReviewStatus.REJECTED, summaries.get(4L).status());
        assertEquals(MediaReviewStatus.DELETED, summaries.get(5L).status());
        verify(imageMapper).selectByIds(List.of(1L, 2L, 3L, 4L, 5L));
    }

    @Test
    void usageReturnsAggregateAndNormalizesNullToZero() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        MediaUsageApiService service = new MediaUsageApiService(imageMapper);
        when(imageMapper.sumImageFileSizeByUserId(9L)).thenReturn(4096L);
        when(imageMapper.sumImageFileSizeByUserId(10L)).thenReturn(null);

        assertEquals(4096L, service.getUserStorageUsageBytes(9L));
        assertEquals(0L, service.getUserStorageUsageBytes(10L));
        verify(imageMapper).sumImageFileSizeByUserId(9L);
        verify(imageMapper).sumImageFileSizeByUserId(10L);
    }

    private static ImageDO image(Long id, Long topicId, short auditStatus) {
        ImageDO image = new ImageDO();
        image.setId(id);
        image.setUserId(8L);
        image.setTopicId(topicId);
        image.setFilename(id == 12L ? "cover.png" : "image-" + id + ".png");
        image.setOssUrl("https://example.test/image-" + id + ".png");
        image.setFileSize(128L);
        image.setIsPublic((short) 1);
        image.setAuditStatus(auditStatus);
        return image;
    }
}
