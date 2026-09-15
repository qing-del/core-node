package com.jacolp.middleware.module.audit.biz.application.service;

import com.jacolp.context.BaseContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.middleware.messaging.event.AuditReviewedEvent;
import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.pulisher.OutboxEventPublisher;
import com.jacolp.module.audit.biz.application.dto.ImageAuditReviewDTO;
import com.jacolp.module.audit.biz.application.service.ImageAuditReviewCompatibilityService;
import com.jacolp.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageAuditReviewCompatibilityServiceTest {
    private ImageAuditMapper imageAuditMapper;
    private OutboxEventPublisher eventPublisher;
    private AuditQueryProjectionMapper projections;
    private ImageAuditReviewCompatibilityService service;

    @BeforeEach
    void setUp() {
        imageAuditMapper = mock(ImageAuditMapper.class);
        eventPublisher = mock(OutboxEventPublisher.class);
        projections = mock(AuditQueryProjectionMapper.class);
        when(projections.selectUsername(9L)).thenReturn("reviewer");
        service = new ImageAuditReviewCompatibilityService(imageAuditMapper, eventPublisher, projections);
        BaseContext.setCurrentId(9L);
    }

    @AfterEach
    void cleanContext() { BaseContext.remove(); }

    @Test
    @SuppressWarnings("unchecked")
    void approvalUpdatesAuditThenUsesTheSameReviewedEventFlow() {
        ImageAuditRecordDO record = pendingRecord();
        when(imageAuditMapper.selectById(10L)).thenReturn(record);
        when(imageAuditMapper.updateAuditRecord(record)).thenReturn(1);

        service.review(new ImageAuditReviewDTO(10L, true, null));

        assertThat(record.getStatus()).isEqualTo(AuditStatus.APPROVED.getCode());
        assertThat(record.getReviewerUserId()).isEqualTo(9L);
        verify(projections).captureReviewer("IMAGE", 10L, "reviewer");
        ArgumentCaptor<List<AuditReviewedEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publishPartitioned(eq(EventTypes.AUDIT_REVIEWED),
                eq(EventTypes.AUDIT_REVIEWED), eq("AUDIT_REVIEW"), eq(10L), eq("10"), events.capture());
        assertThat(events.getValue().getFirst().targetId()).isEqualTo(20L);
        assertThat(events.getValue().getFirst().decision()).isEqualTo(AuditReviewedEvent.Decision.APPROVED);
        InOrder order = inOrder(imageAuditMapper, eventPublisher);
        order.verify(imageAuditMapper).updateAuditRecord(record);
        order.verify(eventPublisher).publishPartitioned(any(), any(), any(), any(), any(), anyList());
    }

    @Test
    void concurrentReviewFailureDoesNotWriteOutbox() {
        ImageAuditRecordDO record = pendingRecord();
        when(imageAuditMapper.selectById(10L)).thenReturn(record);
        when(imageAuditMapper.updateAuditRecord(record)).thenReturn(0);

        assertThatThrownBy(() -> service.review(new ImageAuditReviewDTO(10L, false, "invalid")))
                .isInstanceOf(BaseException.class);
        verify(eventPublisher, never()).publishPartitioned(any(), any(), any(), any(), any(), anyList());
    }

    @Test
    void rejectionWithoutReasonFailsBeforeMutation() {
        when(imageAuditMapper.selectById(10L)).thenReturn(pendingRecord());
        assertThatThrownBy(() -> service.review(new ImageAuditReviewDTO(10L, false, "")))
                .isInstanceOf(BaseException.class);
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
