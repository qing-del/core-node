package com.jacolp.note.api;

import com.jacolp.note.api.command.ApplyMediaRelationAuditCommand;
import com.jacolp.note.api.command.ApplyNoteAuditCommand;
import com.jacolp.note.api.command.ApplyTagAuditCommand;
import com.jacolp.note.api.model.MediaRelationAuditApplyResult;
import com.jacolp.note.api.model.NoteAuditApplyResult;
import com.jacolp.note.api.model.TagAuditApplyResult;

/**
 * Applies audit decisions to note-owned aggregates and their relation rows.
 */
public interface NoteAuditApplyApi {

    NoteAuditApplyResult applyNoteAudit(ApplyNoteAuditCommand command);

    TagAuditApplyResult applyTagAudit(ApplyTagAuditCommand command);

    MediaRelationAuditApplyResult applyMediaRelationAudit(ApplyMediaRelationAuditCommand command);
}
