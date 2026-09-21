package com.jacolp.media.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jacolp.media.api.model.MediaFileIndexSource;
import com.jacolp.media.infrastructure.persistence.dataobject.ImageDO;
import com.jacolp.media.infrastructure.persistence.mapper.ImageMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MediaFileIndexApiServiceTest {

    @Test
    void onlyApprovedPublicImageIsMarkedPublicAndKeepsUrl() {
        ImageMapper mapper = mock(ImageMapper.class);
        when(mapper.selectFileIndexPage(0L, 10)).thenReturn(List.of(image(7L, (short) 2, (short) 1)));

        List<MediaFileIndexSource> result = new MediaFileIndexApiService(mapper).listAfterId(0L, 10);

        assertThat(result).containsExactly(new MediaFileIndexSource(7L, 42L, "cover.png", "https://cdn/cover.png",
                true, false));
    }

    @Test
    void keepsDeletedImageForSoftDeleteProjection() {
        ImageMapper mapper = mock(ImageMapper.class);
        when(mapper.selectFileIndexById(7L)).thenReturn(image(7L, (short) 4, (short) 0));

        Optional<MediaFileIndexSource> result = new MediaFileIndexApiService(mapper).findById(7L);

        assertThat(result).contains(new MediaFileIndexSource(7L, 42L, "cover.png", "https://cdn/cover.png",
                false, true));
    }

    private static ImageDO image(long id, short auditStatus, short isPublic) {
        return new ImageDO(id, 42L, null, "cover.png", "https://cdn/cover.png", (short) 1, 100L, isPublic,
                auditStatus, null);
    }
}
