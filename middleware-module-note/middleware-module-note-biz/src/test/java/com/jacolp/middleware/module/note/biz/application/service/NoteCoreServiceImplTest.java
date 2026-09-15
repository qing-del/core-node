package com.jacolp.middleware.module.note.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.support.TestSecurityContext;
import com.jacolp.note.enums.NoteStatus;
import com.jacolp.audit.api.AuditApplicationApi;
import com.jacolp.audit.api.AuditTargetType;
import com.jacolp.audit.api.CancelAuditApplicationCommand;
import com.jacolp.audit.api.CreateAuditApplicationCommand;
import com.jacolp.note.application.service.NoteCoreServiceImpl;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class NoteCoreServiceImplTest {

    @AfterEach
    void clearContexts() {
        TestSecurityContext.clear();
    }

    @Test
    void submitAuditUsesCasThenCreatesSynchronousApplication() {
        NoteMapper mapper = mock(NoteMapper.class);
        AuditApplicationApi auditApi = mock(AuditApplicationApi.class);
        TestSecurityContext.authenticate(9L, false);
        when(mapper.selectById(7L)).thenReturn(note(7L, 9L, NoteStatus.CONVERTED));
        when(mapper.updateStatusIfCurrent(7L, NoteStatus.CONVERTED.getCode(),
                NoteStatus.PENDING_AUDIT.getCode())).thenReturn(1);

        service(mapper, auditApi).submitNoteAudit(7L);

        ArgumentCaptor<CreateAuditApplicationCommand> command = ArgumentCaptor.forClass(CreateAuditApplicationCommand.class);
        verify(auditApi).createApplication(command.capture());
        assertEquals(AuditTargetType.NOTE, command.getValue().targetType());
        assertEquals(7L, command.getValue().targetId());
        assertEquals(9L, command.getValue().applicantUserId());
        assertEquals("note-7", command.getValue().targetName());
    }

    @Test
    void submitAuditRejectsApprovedStatusBeforeCallingAuditApi() {
        NoteMapper mapper = mock(NoteMapper.class);
        AuditApplicationApi auditApi = mock(AuditApplicationApi.class);
        TestSecurityContext.authenticate(9L, false);
        when(mapper.selectById(7L)).thenReturn(note(7L, 9L, NoteStatus.APPROVED));

        assertThrows(BaseException.class, () -> service(mapper, auditApi).submitNoteAudit(7L));

        verifyNoInteractions(auditApi);
        verify(mapper, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    void getByIdRejectsAnotherUsersPrivateNote() {
        NoteMapper mapper = mock(NoteMapper.class);
        TestSecurityContext.authenticate(9L, false);
        when(mapper.selectById(7L)).thenReturn(note(7L, 10L, NoteStatus.CONVERTED));

        assertThrows(BaseException.class, () -> service(mapper, mock(AuditApplicationApi.class)).getById(7L));
    }

    @Test
    void cancelAuditCancelsApplicationThenUsesCasToRestoreConverted() {
        NoteMapper mapper = mock(NoteMapper.class);
        AuditApplicationApi auditApi = mock(AuditApplicationApi.class);
        TestSecurityContext.authenticate(9L, false);
        when(mapper.selectById(7L)).thenReturn(note(7L, 9L, NoteStatus.PENDING_AUDIT));
        when(mapper.updateStatusIfCurrent(7L, NoteStatus.PENDING_AUDIT.getCode(),
                NoteStatus.CONVERTED.getCode())).thenReturn(1);

        service(mapper, auditApi).cancelNoteAudit(7L);

        ArgumentCaptor<CancelAuditApplicationCommand> command = ArgumentCaptor.forClass(CancelAuditApplicationCommand.class);
        verify(auditApi).cancelApplication(command.capture());
        assertEquals(AuditTargetType.NOTE, command.getValue().targetType());
        assertEquals(9L, command.getValue().actorUserId());
    }

    private static NoteCoreServiceImpl service(NoteMapper mapper, AuditApplicationApi auditApi) {
        NoteCoreServiceImpl service = new NoteCoreServiceImpl();
        ReflectionTestUtils.setField(service, "noteMapper", mapper);
        ReflectionTestUtils.setField(service, "auditApi", auditApi);
        return service;
    }

    private static NoteDO note(Long id, Long userId, NoteStatus status) {
        NoteDO note = new NoteDO();
        note.setId(id);
        note.setUserId(userId);
        note.setTitle("note-" + id);
        note.setStatus(status.getCode());
        return note;
    }
}
