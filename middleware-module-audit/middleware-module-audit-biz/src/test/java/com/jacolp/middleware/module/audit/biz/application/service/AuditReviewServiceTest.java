package com.jacolp.middleware.module.audit.biz.application.service;

import com.jacolp.constant.AuditConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.module.audit.biz.application.dto.AuditBatchReviewDTO;
import com.jacolp.module.audit.biz.application.service.AuditReviewService;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.NoteAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.module.media.api.MediaAuditApplyApi;
import com.jacolp.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.module.media.api.model.MediaAuditApplyResult;
import com.jacolp.module.media.api.model.MediaAuditDecision;
import com.jacolp.module.note.api.NoteAuditApplyApi;
import com.jacolp.module.note.api.command.ApplyTagAuditCommand;
import com.jacolp.module.note.api.command.ApplyNoteAuditCommand;
import com.jacolp.module.note.api.model.AuditDecision;
import com.jacolp.module.note.api.model.TagAuditApplyResult;
import com.jacolp.module.note.api.model.NoteAuditApplyResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditReviewServiceTest {
    private MetaAuditMapper metaMapper;
    private ImageAuditMapper imageMapper;
    private NoteAuditMapper noteMapper;
    private NoteAuditApplyApi noteApi;
    private MediaAuditApplyApi mediaApi;
    private AuditReviewService service;

    @BeforeEach
    void setUp() {
        metaMapper = mock(MetaAuditMapper.class);
        imageMapper = mock(ImageAuditMapper.class);
        noteMapper = mock(NoteAuditMapper.class);
        noteApi = mock(NoteAuditApplyApi.class);
        mediaApi = mock(MediaAuditApplyApi.class);
        service = new AuditReviewService(metaMapper, imageMapper, noteMapper, noteApi, mediaApi);
        BaseContext.setCurrentId(9L);
    }

    @AfterEach
    void cleanContext() { BaseContext.remove(); }

    @Test
    void tagApprovalDeduplicatesIdsAndMapsToApprovedApiDecision() {
        MetaAuditRecordDO record = new MetaAuditRecordDO(); record.setId(10L); record.setTargetId(20L);
        when(metaMapper.selectPendingByIds(List.of(10L))).thenReturn(List.of(record));
        when(metaMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);
        when(noteApi.applyTagAudit(any())).thenReturn(new TagAuditApplyResult(1, 1));

        assertThat(service.batchReviewMeta(new AuditBatchReviewDTO(List.of(10L, 10L), AuditStatus.APPROVED.getCode(), null))).isEqualTo(1);

        verify(metaMapper).batchReviewByIds(List.of(10L), AuditStatus.APPROVED.getCode(), 9L, null);
        ArgumentCaptor<ApplyTagAuditCommand> command = ArgumentCaptor.forClass(ApplyTagAuditCommand.class);
        verify(noteApi).applyTagAudit(command.capture());
        assertThat(command.getValue().tagIds()).containsExactly(20L);
        assertThat(command.getValue().decision()).isEqualTo(AuditDecision.APPROVED);
    }

    @Test
    void rejectionUsesLegacyDefaultReason() {
        MetaAuditRecordDO record = new MetaAuditRecordDO(); record.setId(10L); record.setTargetId(20L);
        when(metaMapper.selectPendingByIds(anyList())).thenReturn(List.of(record));
        when(metaMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);
        when(noteApi.applyTagAudit(any())).thenReturn(new TagAuditApplyResult(1, 1));

        service.batchReviewMeta(new AuditBatchReviewDTO(List.of(10L), AuditStatus.REJECTED.getCode(), "  "));

        verify(metaMapper).batchReviewByIds(anyList(), anyShort(), any(), org.mockito.ArgumentMatchers.eq(AuditConstant.DEFAULT_REJECT_REASON));
    }

    @Test
    void auditRecordAffectedMismatchThrowsBeforeAggregateApiCall() {
        MetaAuditRecordDO record = new MetaAuditRecordDO(); record.setId(10L); record.setTargetId(20L);
        when(metaMapper.selectPendingByIds(anyList())).thenReturn(List.of(record));
        when(metaMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.batchReviewMeta(new AuditBatchReviewDTO(List.of(10L), AuditStatus.APPROVED.getCode(), null)))
                .isInstanceOf(BaseException.class);
        verify(noteApi, never()).applyTagAudit(any());
    }

    @Test
    void imageAndNoteStatusesMapToTheirOwnedApiDecisions() {
        ImageAuditRecordDO image = new ImageAuditRecordDO(); image.setId(11L); image.setImageId(21L);
        when(imageMapper.selectPendingByIds(anyList())).thenReturn(List.of(image));
        when(imageMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);
        when(mediaApi.applyMediaAudit(any())).thenReturn(new MediaAuditApplyResult(1, 1));
        service.batchReviewImage(new AuditBatchReviewDTO(List.of(11L), AuditStatus.REJECTED.getCode(), "no"));
        ArgumentCaptor<ApplyMediaAuditCommand> imageCommand = ArgumentCaptor.forClass(ApplyMediaAuditCommand.class);
        verify(mediaApi).applyMediaAudit(imageCommand.capture());
        assertThat(imageCommand.getValue().decision()).isEqualTo(MediaAuditDecision.REJECTED);
        assertThat(imageCommand.getValue().updateRelationStatus()).isTrue();

        NoteAuditRecordDO note = new NoteAuditRecordDO(); note.setId(12L); note.setNoteId(22L);
        when(noteMapper.selectPendingByIds(anyList())).thenReturn(List.of(note));
        when(noteMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);
        when(noteApi.applyNoteAudit(any())).thenReturn(new NoteAuditApplyResult(1, 1));
        service.batchReviewNote(new AuditBatchReviewDTO(List.of(12L), AuditConstant.REJECT, "no"));
        ArgumentCaptor<ApplyNoteAuditCommand> noteCommand = ArgumentCaptor.forClass(ApplyNoteAuditCommand.class);
        verify(noteApi).applyNoteAudit(noteCommand.capture());
        assertThat(noteCommand.getValue().decision()).isEqualTo(AuditDecision.REJECTED);
    }
}
