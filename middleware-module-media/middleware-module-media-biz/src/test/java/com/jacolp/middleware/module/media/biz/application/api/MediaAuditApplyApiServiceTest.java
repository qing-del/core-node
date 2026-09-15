package com.jacolp.middleware.module.media.biz.application.api;

import com.jacolp.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.module.media.api.model.MediaAuditDecision;
import com.jacolp.module.media.biz.application.api.MediaAuditApplyApiService;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import com.jacolp.module.note.api.NoteAuditApplyApi;
import com.jacolp.module.note.api.command.ApplyMediaRelationAuditCommand;
import com.jacolp.module.note.api.model.AuditDecision;
import com.jacolp.module.note.api.model.MediaRelationAuditApplyResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaAuditApplyApiServiceTest {
    @Test
    void defaultCommandUpdatesMediaAndRelationsWithDeduplicatedIds() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        NoteAuditApplyApi noteApi = mock(NoteAuditApplyApi.class);
        when(imageMapper.updateAuditStatusByIds(List.of(4L), (short) 3)).thenReturn(1);
        when(noteApi.applyMediaRelationAudit(new ApplyMediaRelationAuditCommand(List.of(4L), AuditDecision.REJECTED)))
                .thenReturn(new MediaRelationAuditApplyResult(2));

        var result = new MediaAuditApplyApiService(imageMapper, noteApi).applyMediaAudit(
                new ApplyMediaAuditCommand(List.of(4L, 4L), MediaAuditDecision.REJECTED));

        assertEquals(1, result.mediaRowsUpdated());
        assertEquals(2, result.relationRowsUpdated());
        verify(imageMapper).updateAuditStatusByIds(List.of(4L), (short) 3);
        verify(noteApi).applyMediaRelationAudit(new ApplyMediaRelationAuditCommand(List.of(4L), AuditDecision.REJECTED));
    }

    @Test
    void compatibilityCommandDoesNotUpdateRelations() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        NoteAuditApplyApi noteApi = mock(NoteAuditApplyApi.class);
        when(imageMapper.updateAuditStatusByIds(List.of(4L), (short) 2)).thenReturn(1);

        var result = new MediaAuditApplyApiService(imageMapper, noteApi).applyMediaAudit(
                new ApplyMediaAuditCommand(List.of(4L), MediaAuditDecision.APPROVED, false));

        assertEquals(1, result.mediaRowsUpdated());
        assertEquals(0, result.relationRowsUpdated());
        verify(noteApi, never()).applyMediaRelationAudit(any());
    }
}
