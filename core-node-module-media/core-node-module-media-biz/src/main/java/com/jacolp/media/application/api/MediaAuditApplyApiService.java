package com.jacolp.media.application.api;

import com.jacolp.media.api.MediaAuditApplyApi;
import com.jacolp.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.media.api.model.MediaAuditApplyResult;
import com.jacolp.media.api.model.MediaAuditDecision;
import com.jacolp.media.infrastructure.persistence.mapper.ImageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Legacy media-owned adapter. Note relations are updated independently from AuditReviewedEvent. */
@Service
public class MediaAuditApplyApiService implements MediaAuditApplyApi {
    private static final short MEDIA_APPROVED = 2;
    private static final short MEDIA_REJECTED = 3;

    private final ImageMapper imageMapper;

    public MediaAuditApplyApiService(ImageMapper imageMapper) {
        this.imageMapper = imageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaAuditApplyResult applyMediaAudit(ApplyMediaAuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<Long> ids = normalizeIds(command.mediaIds());
        if (ids.isEmpty()) return new MediaAuditApplyResult(0, 0);

        short status = command.decision() == MediaAuditDecision.APPROVED ? MEDIA_APPROVED : MEDIA_REJECTED;
        int mediaRows = imageMapper.updateAuditStatusByIds(ids, status);
        return new MediaAuditApplyResult(mediaRows, 0);
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        Objects.requireNonNull(ids, "mediaIds must not be null");
        return ids.stream().peek(id -> {
            if (id == null || id <= 0) throw new IllegalArgumentException("mediaIds must contain positive ids only");
        }).distinct().toList();
    }
}
