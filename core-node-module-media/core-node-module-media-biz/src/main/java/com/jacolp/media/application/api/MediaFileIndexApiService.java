package com.jacolp.media.application.api;

import com.jacolp.media.api.MediaFileIndexApi;
import com.jacolp.media.api.model.MediaFileIndexSource;
import com.jacolp.media.infrastructure.persistence.dataobject.ImageDO;
import com.jacolp.media.infrastructure.persistence.mapper.ImageMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 媒体模块拥有的 file 索引源读适配器。 */
@Service
public class MediaFileIndexApiService implements MediaFileIndexApi {

    private static final short IMAGE_PUBLIC = 1;
    private static final short IMAGE_APPROVED = 2;
    private static final short IMAGE_DELETED = 4;

    private final ImageMapper imageMapper;

    /** 保存图片 persistence 查询依赖。 */
    public MediaFileIndexApiService(ImageMapper imageMapper) {
        this.imageMapper = Objects.requireNonNull(imageMapper, "imageMapper must not be null");
    }

    /** 按主键游标返回包含软删除状态的索引源。 */
    @Override
    public List<MediaFileIndexSource> listAfterId(long afterId, int limit) {
        requireNonNegative(afterId, "afterId");
        requirePositive(limit, "limit");
        return imageMapper.selectFileIndexPage(afterId, limit).stream()
                .map(MediaFileIndexApiService::toSource).toList();
    }

    /** 读取单个图片的当前索引源；硬删除后的空记录由调用方解释为删除 ES 文档。 */
    @Override
    public Optional<MediaFileIndexSource> findById(long mediaId) {
        requirePositive(mediaId, "mediaId");
        return Optional.ofNullable(imageMapper.selectFileIndexById(mediaId)).map(MediaFileIndexApiService::toSource);
    }

    /** 将图片审核与公开状态映射为 file 索引语义。 */
    private static MediaFileIndexSource toSource(ImageDO image) {
        if (image == null) {
            throw new IllegalArgumentException("media index source must not be null");
        }
        Short auditStatus = Objects.requireNonNull(image.getAuditStatus(), "image auditStatus must not be null");
        boolean approved = auditStatus == IMAGE_APPROVED;
        return new MediaFileIndexSource(image.getId(), image.getUserId(), image.getFilename(), image.getOssUrl(),
                Short.valueOf(IMAGE_PUBLIC).equals(image.getIsPublic()) && approved,
                auditStatus == IMAGE_DELETED);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
