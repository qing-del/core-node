package com.jacolp.middleware.module.audit.biz.application.service;

import com.jacolp.context.BaseContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.module.audit.biz.application.dto.ImageAuditReviewDTO;
import com.jacolp.module.audit.biz.application.service.ImageAuditReviewCompatibilityService;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.module.media.api.MediaAuditApplyApi;
import com.jacolp.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.module.media.api.model.MediaAuditApplyResult;
import com.jacolp.module.media.api.model.MediaAuditDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageAuditReviewCompatibilityServiceTest {
    private ImageAuditMapper imageAuditMapper;
    private MediaAuditApplyApi mediaAuditApplyApi;
    private ImageAuditReviewCompatibilityService service;

    @BeforeEach
    void setUp() {
        imageAuditMapper = mock(ImageAuditMapper.class);
        mediaAuditApplyApi = mock(MediaAuditApplyApi.class);
        service = new ImageAuditReviewCompatibilityService(imageAuditMapper, mediaAuditApplyApi);
        BaseContext.setCurrentId(9L);
    }

    @AfterEach
    void cleanContext() {
        BaseContext.remove();
    }

    @Test
    void approvalUsesSingleImageMediaCommandThenUpdatesAuditRecordWithReviewer() {
        ImageAuditRecordDO record = pendingRecord();
        when(imageAuditMapper.selectById(10L)).thenReturn(record);
        when(mediaAuditApplyApi.applyMediaAudit(any())).thenReturn(new MediaAuditApplyResult(1, 0));

        service.review(new ImageAuditReviewDTO(10L, true, null));

        ArgumentCaptor<ApplyMediaAuditCommand> command = ArgumentCaptor.forClass(ApplyMediaAuditCommand.class);
        verify(mediaAuditApplyApi).applyMediaAudit(command.capture());
        assertThat(command.getValue().mediaIds()).containsExactly(20L);
        assertThat(command.getValue().decision()).isEqualTo(MediaAuditDecision.APPROVED);
        assertThat(command.getValue().updateRelationStatus()).isFalse();
        assertThat(record.getStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(record.getReviewerUserId()).isEqualTo(9L);
        assertThat(record.getReviewTime()).isNotNull();
        InOrder order = inOrder(mediaAuditApplyApi, imageAuditMapper);
        order.verify(mediaAuditApplyApi).applyMediaAudit(any());
        order.verify(imageAuditMapper).updateAuditRecord(record);
    }

    @Test
    void rejectionPersistsReasonAndRejectedDecision() {
        ImageAuditRecordDO record = pendingRecord();
        when(imageAuditMapper.selectById(10L)).thenReturn(record);
        when(mediaAuditApplyApi.applyMediaAudit(any())).thenReturn(new MediaAuditApplyResult(1, 0));

        service.review(new ImageAuditReviewDTO(10L, false, "invalid image"));

        ArgumentCaptor<ApplyMediaAuditCommand> command = ArgumentCaptor.forClass(ApplyMediaAuditCommand.class);
        verify(mediaAuditApplyApi).applyMediaAudit(command.capture());
        assertThat(command.getValue().decision()).isEqualTo(MediaAuditDecision.REJECTED);
        assertThat(record.getStatus()).isEqualTo(AuditStatus.REJECTED.getCode());
        assertThat(record.getRejectReason()).isEqualTo("invalid image");
    }

    @Test
    void rejectionWithoutReasonFailsBeforeMediaUpdate() {
        when(imageAuditMapper.selectById(10L)).thenReturn(pendingRecord());

        assertThatThrownBy(() -> service.review(new ImageAuditReviewDTO(10L, false, "")))
                .isInstanceOf(BaseException.class);

        verify(mediaAuditApplyApi, never()).applyMediaAudit(any());
        verify(imageAuditMapper, never()).updateAuditRecord(any());
    }

    @Test
    void processedAuditFailsBeforeMediaUpdate() {
        ImageAuditRecordDO record = pendingRecord();
        record.setStatus(AuditStatus.APPROVED.getCode());
        when(imageAuditMapper.selectById(10L)).thenReturn(record);

        assertThatThrownBy(() -> service.review(new ImageAuditReviewDTO(10L, true, null)))
                .isInstanceOf(BaseException.class);

        verify(mediaAuditApplyApi, never()).applyMediaAudit(any());
        verify(imageAuditMapper, never()).updateAuditRecord(any());
    }

    private static ImageAuditRecordDO pendingRecord() {
        ImageAuditRecordDO record = new ImageAuditRecordDO();
        record.setId(10L);
        record.setImageId(20L);
        record.setStatus(AuditStatus.AUDITING.getCode());
        return record;
    }
}
