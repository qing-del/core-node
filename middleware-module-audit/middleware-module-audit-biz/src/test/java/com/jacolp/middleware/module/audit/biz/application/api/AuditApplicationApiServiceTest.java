package com.jacolp.middleware.module.audit.biz.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.audit.api.AuditApplicationResult;
import com.jacolp.audit.api.AuditTargetType;
import com.jacolp.audit.api.CancelAuditApplicationCommand;
import com.jacolp.audit.api.CreateAuditApplicationCommand;
import com.jacolp.audit.application.api.AuditApplicationApiService;
import com.jacolp.audit.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.dataobject.NoteAuditRecordDO;
import com.jacolp.audit.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.audit.infrastructure.persistence.mapper.NoteAuditMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditApplicationApiServiceTest {

    private MetaAuditMapper metaAuditMapper;
    private ImageAuditMapper imageAuditMapper;
    private NoteAuditMapper noteAuditMapper;
    private AuditQueryProjectionMapper projections;
    private AuditApplicationApiService adapter;

    @BeforeEach
    void setUp() {
        metaAuditMapper = mock(MetaAuditMapper.class);
        imageAuditMapper = mock(ImageAuditMapper.class);
        noteAuditMapper = mock(NoteAuditMapper.class);
        projections = mock(AuditQueryProjectionMapper.class);
        adapter = new AuditApplicationApiService(metaAuditMapper, imageAuditMapper, noteAuditMapper, projections);
    }

    @Test
    void createNoteWritesFrozenProjectionAndCancelMarksRecordCancelled() {
        when(noteAuditMapper.countPendingAuditByNoteId(13L)).thenReturn(0);
        doAnswer(invocation -> {
            invocation.<NoteAuditRecordDO>getArgument(0).setId(103L);
            return 1;
        }).when(noteAuditMapper).insertAuditRecord(any(NoteAuditRecordDO.class));
        when(projections.selectUsername(24L)).thenReturn("applicant");
        when(noteAuditMapper.selectPendingByNoteId(13L)).thenReturn(noteRecord(103L, 24L, (short) 0));
        when(noteAuditMapper.cancelPendingByNoteId(13L, (short) 3)).thenReturn(1);

        AuditApplicationResult created = adapter.createApplication(
                new CreateAuditApplicationCommand(AuditTargetType.NOTE, 13L, 24L, "reason", "Note title", null));
        adapter.cancelApplication(new CancelAuditApplicationCommand(AuditTargetType.NOTE, 13L, 24L));

        assertThat(created.auditApplicationId()).isEqualTo(103L);
        verify(projections).upsertRecord("NOTE", 103L, 13L, "applicant", "Note title", null);
        verify(noteAuditMapper).cancelPendingByNoteId(13L, (short) 3);
    }

    @Test
    void createTagAndImageUseCancelledStatusFive() {
        when(metaAuditMapper.countPendingAuditByApplyTypeAndTargetId((short) 2, 11L)).thenReturn(0);
        doAnswer(invocation -> {
            invocation.<MetaAuditRecordDO>getArgument(0).setId(101L);
            return 1;
        }).when(metaAuditMapper).insertAuditRecord(any(MetaAuditRecordDO.class));
        when(metaAuditMapper.selectPendingByApplyTypeAndTargetId((short) 2, 11L))
                .thenReturn(metaRecord(101L, 22L, (short) 1));
        when(metaAuditMapper.cancelPendingByApplyTypeAndTargetId((short) 2, 11L, (short) 5)).thenReturn(1);
        when(imageAuditMapper.countPendingAuditByImageId(12L)).thenReturn(0);
        doAnswer(invocation -> {
            invocation.<ImageAuditRecordDO>getArgument(0).setId(102L);
            return 1;
        }).when(imageAuditMapper).insertAuditRecord(any(ImageAuditRecordDO.class));
        when(imageAuditMapper.selectPendingByImageId(12L)).thenReturn(imageRecord(102L, 23L, (short) 1));
        when(imageAuditMapper.cancelPendingByImageId(12L, (short) 5)).thenReturn(1);

        adapter.createApplication(new CreateAuditApplicationCommand(AuditTargetType.TAG, 11L, 22L, null, "tag", null));
        adapter.cancelApplication(new CancelAuditApplicationCommand(AuditTargetType.TAG, 11L, 22L));
        adapter.createApplication(new CreateAuditApplicationCommand(AuditTargetType.IMAGE, 12L, 23L, null, "image", "url"));
        adapter.cancelApplication(new CancelAuditApplicationCommand(AuditTargetType.IMAGE, 12L, 23L));

        verify(metaAuditMapper).cancelPendingByApplyTypeAndTargetId((short) 2, 11L, (short) 5);
        verify(imageAuditMapper).cancelPendingByImageId(12L, (short) 5);
    }

    @Test
    void createRejectsExistingPendingApplication() {
        when(imageAuditMapper.countPendingAuditByImageId(12L)).thenReturn(1);

        assertThatThrownBy(() -> adapter.createApplication(
                new CreateAuditApplicationCommand(AuditTargetType.IMAGE, 12L, 23L, null, "image", null)))
                .isInstanceOf(RuntimeException.class);

        verify(imageAuditMapper, never()).insertAuditRecord(any());
    }

    @Test
    void cancellationRejectsActorOtherThanApplicant() {
        when(imageAuditMapper.selectPendingByImageId(12L)).thenReturn(imageRecord(102L, 23L, (short) 1));

        assertThatThrownBy(() -> adapter.cancelApplication(
                new CancelAuditApplicationCommand(AuditTargetType.IMAGE, 12L, 99L)))
                .isInstanceOf(RuntimeException.class);

        verify(imageAuditMapper, never()).cancelPendingByImageId(eq(12L), any());
    }

    private static MetaAuditRecordDO metaRecord(Long id, Long applicantUserId, Short status) {
        MetaAuditRecordDO record = new MetaAuditRecordDO();
        record.setId(id);
        record.setApplicantUserId(applicantUserId);
        record.setStatus(status);
        return record;
    }

    private static ImageAuditRecordDO imageRecord(Long id, Long applicantUserId, Short status) {
        ImageAuditRecordDO record = new ImageAuditRecordDO();
        record.setId(id);
        record.setApplicantUserId(applicantUserId);
        record.setStatus(status);
        return record;
    }

    private static NoteAuditRecordDO noteRecord(Long id, Long applicantUserId, Short status) {
        NoteAuditRecordDO record = new NoteAuditRecordDO();
        record.setId(id);
        record.setApplicantUserId(applicantUserId);
        record.setStatus(status);
        return record;
    }
}
