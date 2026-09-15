package com.jacolp.module.media.biz.application.api;

import com.jacolp.module.media.api.MediaAuditApplyApi;
import com.jacolp.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.module.media.api.model.MediaAuditApplyResult;
import com.jacolp.module.media.api.model.MediaAuditDecision;
import com.jacolp.module.note.api.NoteAuditApplyApi;
import com.jacolp.module.note.api.command.ApplyMediaRelationAuditCommand;
import com.jacolp.module.note.api.model.AuditDecision;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Formal media-owned audit writer. Relation updates cross the module boundary only through NoteAuditApplyApi. */
@Service
public class MediaAuditApplyApiService implements MediaAuditApplyApi {
    private static final short MEDIA_APPROVED = 2;
    private static final short MEDIA_REJECTED = 3;

    private final ImageMapper imageMapper;
    private final NoteAuditApplyApi noteAuditApplyApi;

    public MediaAuditApplyApiService(ImageMapper imageMapper, NoteAuditApplyApi noteAuditApplyApi) {
        this.imageMapper = imageMapper;
        this.noteAuditApplyApi = noteAuditApplyApi;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaAuditApplyResult applyMediaAudit(ApplyMediaAuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<Long> ids = normalizeIds(command.mediaIds());
        if (ids.isEmpty()) return new MediaAuditApplyResult(0, 0);

        short status = command.decision() == MediaAuditDecision.APPROVED ? MEDIA_APPROVED : MEDIA_REJECTED;
        int mediaRows = imageMapper.updateAuditStatusByIds(ids, status);
        int relationRows = command.updateRelationStatus()
                ? noteAuditApplyApi.applyMediaRelationAudit(new ApplyMediaRelationAuditCommand(ids, toNoteDecision(command.decision())))
                    .relationRowsUpdated()
                : 0;
        return new MediaAuditApplyResult(mediaRows, relationRows);
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        Objects.requireNonNull(ids, "mediaIds must not be null");
        return ids.stream().peek(id -> {
            if (id == null || id <= 0) throw new IllegalArgumentException("mediaIds must contain positive ids only");
        }).distinct().toList();
    }

    private static AuditDecision toNoteDecision(MediaAuditDecision decision) {
        return decision == MediaAuditDecision.APPROVED ? AuditDecision.APPROVED : AuditDecision.REJECTED;
    }
}
