package com.jacolp.middleware.module.media.biz.application.api;

import com.jacolp.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.module.media.api.model.MediaAuditDecision;
import com.jacolp.module.media.biz.application.api.MediaAuditApplyApiService;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MediaAuditApplyApiServiceTest {
    @Test
    void defaultCommandUpdatesOnlyMediaWithDeduplicatedIds() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.updateAuditStatusByIds(List.of(4L), (short) 3)).thenReturn(1);

        var result = new MediaAuditApplyApiService(imageMapper).applyMediaAudit(
                new ApplyMediaAuditCommand(List.of(4L, 4L), MediaAuditDecision.REJECTED));

        assertEquals(1, result.mediaRowsUpdated());
        assertEquals(0, result.relationRowsUpdated());
        verify(imageMapper).updateAuditStatusByIds(List.of(4L), (short) 3);
    }

    @Test
    void compatibilityCommandDoesNotUpdateRelations() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.updateAuditStatusByIds(List.of(4L), (short) 2)).thenReturn(1);

        var result = new MediaAuditApplyApiService(imageMapper).applyMediaAudit(
                new ApplyMediaAuditCommand(List.of(4L), MediaAuditDecision.APPROVED, false));

        assertEquals(1, result.mediaRowsUpdated());
        assertEquals(0, result.relationRowsUpdated());
    }
}
