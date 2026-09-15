package com.jacolp.note.application.api;

import com.jacolp.note.infrastructure.persistence.mapper.NoteEachMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteImageMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteTagMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.TagMapper;
import com.jacolp.note.api.NoteAuditApplyApi;
import com.jacolp.note.api.command.ApplyNoteAuditCommand;
import com.jacolp.note.api.command.ApplyTagAuditCommand;
import com.jacolp.note.api.command.ApplyMediaRelationAuditCommand;
import com.jacolp.note.api.model.AuditDecision;
import com.jacolp.note.api.model.NoteAuditApplyResult;
import com.jacolp.note.api.model.TagAuditApplyResult;
import com.jacolp.note.api.model.MediaRelationAuditApplyResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Note-owned audit writer preserving the legacy note and relation-status mappings.
 */
@Component
public class NoteAuditApplyApiService implements NoteAuditApplyApi {

    private static final short NOTE_APPROVED = 5;
    private static final short NOTE_REJECTED = 7;
    private static final short LEGACY_RELATION_APPROVED = 1;
    private static final short LEGACY_RELATION_REJECTED = 2;
    private static final short RESOURCE_APPROVED = 2;
    private static final short RESOURCE_REJECTED = 3;

    private final NoteMapper noteMapper;
    private final TagMapper tagMapper;
    private final NoteEachMappingMapper noteEachMappingMapper;
    private final NoteTagMappingMapper noteTagMappingMapper;
    private final NoteImageMappingMapper noteImageMappingMapper;

    public NoteAuditApplyApiService(NoteMapper noteMapper, TagMapper tagMapper,
                                    NoteEachMappingMapper noteEachMappingMapper,
                                    NoteTagMappingMapper noteTagMappingMapper,
                                    NoteImageMappingMapper noteImageMappingMapper) {
        this.noteMapper = noteMapper;
        this.tagMapper = tagMapper;
        this.noteEachMappingMapper = noteEachMappingMapper;
        this.noteTagMappingMapper = noteTagMappingMapper;
        this.noteImageMappingMapper = noteImageMappingMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteAuditApplyResult applyNoteAudit(ApplyNoteAuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<Long> ids = normalizeIds(command.noteIds(), "noteIds");
        if (ids.isEmpty()) {
            return new NoteAuditApplyResult(0, 0);
        }
        int noteRows = noteMapper.updateStatusByIds(ids,
                command.decision() == AuditDecision.APPROVED ? NOTE_APPROVED : NOTE_REJECTED);
        int relationRows = noteEachMappingMapper.updateBySourceNoteIds(ids,
                command.decision() == AuditDecision.APPROVED
                        ? LEGACY_RELATION_APPROVED : LEGACY_RELATION_REJECTED);
        return new NoteAuditApplyResult(noteRows, relationRows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagAuditApplyResult applyTagAudit(ApplyTagAuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<Long> ids = normalizeIds(command.tagIds(), "tagIds");
        if (ids.isEmpty()) {
            return new TagAuditApplyResult(0, 0);
        }
        short status = command.decision() == AuditDecision.APPROVED ? RESOURCE_APPROVED : RESOURCE_REJECTED;
        int tagRows = tagMapper.updateAuditStatusByIds(ids, status);
        int relationRows = noteTagMappingMapper.updateByTagIds(ids, status);
        return new TagAuditApplyResult(tagRows, relationRows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaRelationAuditApplyResult applyMediaRelationAudit(ApplyMediaRelationAuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<Long> ids = normalizeIds(command.mediaIds(), "mediaIds");
        if (ids.isEmpty()) return new MediaRelationAuditApplyResult(0);
        short status = command.decision() == AuditDecision.APPROVED ? RESOURCE_APPROVED : RESOURCE_REJECTED;
        return new MediaRelationAuditApplyResult(noteImageMappingMapper.updateByImageIds(ids, status));
    }

    private static List<Long> normalizeIds(List<Long> ids, String name) {
        Objects.requireNonNull(ids, name + " must not be null");
        return ids.stream()
                .peek(id -> {
                    if (id == null || id <= 0) {
                        throw new IllegalArgumentException(name + " must contain positive ids only");
                    }
                })
                .distinct()
                .toList();
    }
}
