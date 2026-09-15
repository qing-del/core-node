package com.jacolp.module.media.biz.application.api;

import com.jacolp.module.media.api.MediaFileApi;
import com.jacolp.module.media.api.command.MediaFileLookupCommand;
import com.jacolp.module.media.api.model.MediaFileSummary;
import com.jacolp.module.media.api.model.MediaReviewStatus;
import com.jacolp.module.media.biz.infrastructure.persistence.dataobject.ImageDO;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MediaFileApiService implements MediaFileApi {
    private final ImageMapper imageMapper;

    public MediaFileApiService(ImageMapper imageMapper) {
        this.imageMapper = imageMapper;
    }

    @Override
    public Map<Long, MediaFileSummary> findByIds(Collection<Long> mediaIds) {
        List<Long> ids = normalizeIds(mediaIds, "mediaIds");
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, MediaFileSummary> summaries = new LinkedHashMap<>();
        for (ImageDO image : imageMapper.selectByIds(ids)) {
            summaries.put(image.getId(), toSummary(image));
        }
        return Map.copyOf(summaries);
    }

    @Override
    public Map<String, MediaFileSummary> findByOwnerTopicAndFilenames(MediaFileLookupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requirePositive(command.userId(), "userId");
        List<String> filenames = normalizeFilenames(command.filenames());
        if (filenames.isEmpty()) {
            return Map.of();
        }
        Map<String, MediaFileSummary> summaries = new LinkedHashMap<>();
        for (ImageDO image : imageMapper.selectByUserIdAndTopicIdAndFilenames(command.userId(), command.topicId(), filenames)) {
            summaries.put(image.getFilename(), toSummary(image));
        }
        return Map.copyOf(summaries);
    }

    private static MediaFileSummary toSummary(ImageDO image) {
        return new MediaFileSummary(image.getId(), image.getUserId(), image.getTopicId(), image.getFilename(),
                image.getOssUrl(), image.getFileSize() == null ? 0L : image.getFileSize(),
                Short.valueOf((short) 1).equals(image.getIsPublic()), toStatus(image.getAuditStatus()));
    }

    private static List<Long> normalizeIds(Collection<Long> ids, String name) {
        Objects.requireNonNull(ids, name + " must not be null");
        return ids.stream().peek(id -> requirePositive(id, name)).distinct().toList();
    }

    private static List<String> normalizeFilenames(List<String> filenames) {
        Objects.requireNonNull(filenames, "filenames must not be null");
        return filenames.stream().peek(filename -> {
            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException("filenames must contain non-blank names only");
            }
        }).distinct().toList();
    }

    private static void requirePositive(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static MediaReviewStatus toStatus(Short status) {
        return switch (status) {
            case 0 -> MediaReviewStatus.WAITING;
            case 1 -> MediaReviewStatus.REVIEWING;
            case 2 -> MediaReviewStatus.APPROVED;
            case 3 -> MediaReviewStatus.REJECTED;
            case 4 -> MediaReviewStatus.DELETED;
            default -> throw new IllegalArgumentException("Unsupported legacy media status: " + status);
        };
    }
}
