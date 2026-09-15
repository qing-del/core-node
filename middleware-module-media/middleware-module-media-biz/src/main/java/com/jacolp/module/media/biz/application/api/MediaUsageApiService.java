package com.jacolp.module.media.biz.application.api;

import com.jacolp.module.media.api.MediaUsageApi;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import org.springframework.stereotype.Service;

@Service
public class MediaUsageApiService implements MediaUsageApi {
    private final ImageMapper imageMapper;

    public MediaUsageApiService(ImageMapper imageMapper) {
        this.imageMapper = imageMapper;
    }

    @Override
    public long getUserStorageUsageBytes(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Long usage = imageMapper.sumImageFileSizeByUserId(userId);
        return usage == null ? 0L : usage;
    }
}
