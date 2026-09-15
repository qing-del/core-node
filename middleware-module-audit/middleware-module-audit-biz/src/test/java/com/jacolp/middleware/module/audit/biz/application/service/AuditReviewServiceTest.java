package com.jacolp.middleware.module.audit.biz.application.service;

import com.jacolp.common.core.constant.AuditConstant;
import com.jacolp.common.core.enums.AuditStatus;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.event.AuditReviewedEvent;
import com.jacolp.audit.support.TestSecurityContext;
import com.jacolp.common.messaging.pulisher.OutboxEventPublisher;
import com.jacolp.audit.application.dto.AuditBatchReviewDTO;
import com.jacolp.audit.application.service.AuditReviewService;
import com.jacolp.audit.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.dataobject.NoteAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditReviewServiceTest {
    private MetaAuditMapper metaMapper;
    private ImageAuditMapper imageMapper;
    private NoteAuditMapper noteMapper;
    private OutboxEventPublisher eventPublisher;
    private AuditQueryProjectionMapper projections;
    private AuditReviewService service;

    @BeforeEach
    void setUp() {
        metaMapper = mock(MetaAuditMapper.class);
        imageMapper = mock(ImageAuditMapper.class);
        noteMapper = mock(NoteAuditMapper.class);
        eventPublisher = mock(OutboxEventPublisher.class);
        projections = mock(AuditQueryProjectionMapper.class);
        when(projections.selectUsername(9L)).thenReturn("reviewer");
        service = new AuditReviewService(metaMapper, imageMapper, noteMapper, eventPublisher, projections);
        TestSecurityContext.authenticate(9L, false);
    }

    @AfterEach
    void cleanContext() { TestSecurityContext.clear(); }

    @Test
    @SuppressWarnings("unchecked")
    void tagApprovalUpdatesAuditAndWritesBusinessEventShard() {
        MetaAuditRecordDO record = new MetaAuditRecordDO();
        record.setId(10L); record.setTargetId(20L);
        when(metaMapper.selectPendingByIds(List.of(10L))).thenReturn(List.of(record));
        when(metaMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);

        assertThat(service.batchReviewMeta(new AuditBatchReviewDTO(
                List.of(10L, 10L), AuditStatus.APPROVED.getCode(), null))).isEqualTo(1);

        verify(metaMapper).batchReviewByIds(List.of(10L), AuditStatus.APPROVED.getCode(), 9L, null);
        verify(projections).captureReviewer("TAG", 10L, "reviewer");
        ArgumentCaptor<List<AuditReviewedEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publishPartitioned(eq(EventTypes.AUDIT_REVIEWED),
                eq(EventTypes.AUDIT_REVIEWED), eq("AUDIT_REVIEW"), eq(10L), any(), events.capture());
        assertThat(events.getValue()).singleElement().satisfies(event -> {
            assertThat(event.auditId()).isEqualTo(10L);
            assertThat(event.targetType()).isEqualTo(AuditReviewedEvent.TargetType.TAG);
            assertThat(event.targetId()).isEqualTo(20L);
            assertThat(event.decision()).isEqualTo(AuditReviewedEvent.Decision.APPROVED);
            assertThat(event.reviewerUserId()).isEqualTo(9L);
        });
    }

    @Test
    void rejectionUsesLegacyDefaultReasonInAuditAndEvent() {
        MetaAuditRecordDO record = new MetaAuditRecordDO();
        record.setId(10L); record.setTargetId(20L);
        when(metaMapper.selectPendingByIds(anyList())).thenReturn(List.of(record));
        when(metaMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);

        service.batchReviewMeta(new AuditBatchReviewDTO(List.of(10L),
                AuditStatus.REJECTED.getCode(), "  "));

        verify(metaMapper).batchReviewByIds(anyList(), anyShort(), any(),
                eq(AuditConstant.DEFAULT_REJECT_REASON));
    }

    @Test
    void auditRecordAffectedMismatchFailsBeforeOutboxWrite() {
        MetaAuditRecordDO record = new MetaAuditRecordDO();
        record.setId(10L); record.setTargetId(20L);
        when(metaMapper.selectPendingByIds(anyList())).thenReturn(List.of(record));
        when(metaMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.batchReviewMeta(new AuditBatchReviewDTO(
                List.of(10L), AuditStatus.APPROVED.getCode(), null)))
                .isInstanceOf(BaseException.class);
        verify(eventPublisher, never()).publishPartitioned(any(), any(), any(), any(), any(), anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageAndNoteUseSameOutboxMechanism() {
        ImageAuditRecordDO image = new ImageAuditRecordDO();
        image.setId(11L); image.setImageId(21L);
        when(imageMapper.selectPendingByIds(anyList())).thenReturn(List.of(image));
        when(imageMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);
        service.batchReviewImage(new AuditBatchReviewDTO(
                List.of(11L), AuditStatus.REJECTED.getCode(), "no"));

        NoteAuditRecordDO note = new NoteAuditRecordDO();
        note.setId(12L); note.setNoteId(22L);
        when(noteMapper.selectPendingByIds(anyList())).thenReturn(List.of(note));
        when(noteMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);
        service.batchReviewNote(new AuditBatchReviewDTO(
                List.of(12L), AuditConstant.REJECT, "no"));

        ArgumentCaptor<List<AuditReviewedEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishPartitioned(
                eq(EventTypes.AUDIT_REVIEWED), eq(EventTypes.AUDIT_REVIEWED),
                eq("AUDIT_REVIEW"), any(), any(), events.capture());
        assertThat(events.getAllValues().get(0).getFirst().targetType())
                .isEqualTo(AuditReviewedEvent.TargetType.IMAGE);
        assertThat(events.getAllValues().get(1).getFirst().targetType())
                .isEqualTo(AuditReviewedEvent.TargetType.NOTE);
    }

    @Test
    void outboxFailurePropagatesSoTransactionInterceptorCanRollbackAuditUpdate() {
        MetaAuditRecordDO record = new MetaAuditRecordDO();
        record.setId(10L); record.setTargetId(20L);
        when(metaMapper.selectPendingByIds(anyList())).thenReturn(List.of(record));
        when(metaMapper.batchReviewByIds(anyList(), anyShort(), any(), any())).thenReturn(1);
        when(eventPublisher.publishPartitioned(any(), any(), any(), any(), any(), anyList()))
                .thenThrow(new IllegalStateException("outbox unavailable"));

        assertThatThrownBy(() -> service.batchReviewMeta(new AuditBatchReviewDTO(
                List.of(10L), AuditStatus.APPROVED.getCode(), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");
    }
}
