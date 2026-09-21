package com.jacolp.media.api;

import com.jacolp.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.media.api.model.MediaAuditApplyResult;

/**
 * Applies audit decisions to media files and their note-image relation rows.
 */
public interface MediaAuditApplyApi {

    MediaAuditApplyResult applyMediaAudit(ApplyMediaAuditCommand command);
}
