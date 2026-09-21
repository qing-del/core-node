package com.jacolp.media.api.model;

/** 图片 file 索引所需的跨模块最小数据。 */
public record MediaFileIndexSource(Long id, Long ownerUserId, String fileName, String resourceUrl,
                                   boolean publicResource, boolean deleted) {

    /** 校验索引源的身份、名称和 URL 字段。 */
    public MediaFileIndexSource {
        if (id == null || id <= 0 || ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("media index source IDs must be positive");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("media index source fileName must not be blank");
        }
        if (resourceUrl == null || resourceUrl.isBlank()) {
            throw new IllegalArgumentException("media index source resourceUrl must not be blank");
        }
    }
}
