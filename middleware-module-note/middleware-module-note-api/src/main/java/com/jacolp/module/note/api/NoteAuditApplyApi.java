package com.jacolp.module.note.api;

import com.jacolp.module.note.api.command.ApplyNoteAuditCommand;
import com.jacolp.module.note.api.command.ApplyTagAuditCommand;
import com.jacolp.module.note.api.command.ApplyMediaRelationAuditCommand;
import com.jacolp.module.note.api.model.MediaRelationAuditApplyResult;
import com.jacolp.module.note.api.model.NoteAuditApplyResult;
import com.jacolp.module.note.api.model.TagAuditApplyResult;

/**
 * Applies audit decisions to note-owned aggregates and their relation rows.
 */
public interface NoteAuditApplyApi {

    NoteAuditApplyResult applyNoteAudit(ApplyNoteAuditCommand command);

    TagAuditApplyResult applyTagAudit(ApplyTagAuditCommand command);

    MediaRelationAuditApplyResult applyMediaRelationAudit(ApplyMediaRelationAuditCommand command);
}
