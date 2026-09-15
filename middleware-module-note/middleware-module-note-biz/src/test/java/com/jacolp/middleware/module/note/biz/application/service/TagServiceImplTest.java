package com.jacolp.middleware.module.note.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.module.audit.api.AuditApplicationApi;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.module.audit.api.CreateAuditApplicationCommand;
import com.jacolp.module.audit.api.PendingAuditApplicationQuery;
import com.jacolp.module.note.biz.application.service.TagServiceImpl;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TagDO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TagMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class TagServiceImplTest {

    @AfterEach
    void clearContexts() {
        BaseContext.remove();
        PermissionContext.remove();
    }

    @Test
    void submitAuditUsesTagApplicantAndMovesTagToAuditingAfterApplicationCreation() {
        TagMapper mapper = org.mockito.Mockito.mock(TagMapper.class);
        AuditApplicationApi auditApi = org.mockito.Mockito.mock(AuditApplicationApi.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.WAIT));
        when(auditApi.hasPendingApplication(any(PendingAuditApplicationQuery.class))).thenReturn(false);

        service(mapper, auditApi).submitTagAudit(7L);

        ArgumentCaptor<CreateAuditApplicationCommand> command = ArgumentCaptor.forClass(CreateAuditApplicationCommand.class);
        verify(auditApi).createApplication(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(AuditTargetType.TAG, command.getValue().targetType());
        org.junit.jupiter.api.Assertions.assertEquals(7L, command.getValue().targetId());
        org.junit.jupiter.api.Assertions.assertEquals(9L, command.getValue().applicantUserId());
        verify(mapper).updateAuditStatusByIds(List.of(7L), AuditStatus.AUDITING.getCode());
    }

    @Test
    void submitAuditRejectsExistingPendingApplicationBeforeChangingTagStatus() {
        TagMapper mapper = org.mockito.Mockito.mock(TagMapper.class);
        AuditApplicationApi auditApi = org.mockito.Mockito.mock(AuditApplicationApi.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.WAIT));
        when(auditApi.hasPendingApplication(any(PendingAuditApplicationQuery.class))).thenReturn(true);

        assertThrows(BaseException.class, () -> service(mapper, auditApi).submitTagAudit(7L));

        verify(auditApi, never()).createApplication(any());
        verify(mapper, never()).updateAuditStatusByIds(any(), any());
    }

    @Test
    void cancelAuditUsesCurrentUserAndMovesTagBackToWaiting() {
        TagMapper mapper = org.mockito.Mockito.mock(TagMapper.class);
        AuditApplicationApi auditApi = org.mockito.Mockito.mock(AuditApplicationApi.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.AUDITING));

        service(mapper, auditApi).cancelTagAudit(7L);

        ArgumentCaptor<CancelAuditApplicationCommand> command = ArgumentCaptor.forClass(CancelAuditApplicationCommand.class);
        verify(auditApi).cancelApplication(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(AuditTargetType.TAG, command.getValue().targetType());
        org.junit.jupiter.api.Assertions.assertEquals(9L, command.getValue().actorUserId());
        verify(mapper).updateAuditStatusByIds(List.of(7L), AuditStatus.WAIT.getCode());
    }

    private static TagServiceImpl service(TagMapper mapper, AuditApplicationApi auditApi) {
        TagServiceImpl service = new TagServiceImpl();
        ReflectionTestUtils.setField(service, "tagMapper", mapper);
        ReflectionTestUtils.setField(service, "auditApplicationApi", auditApi);
        return service;
    }

    private static TagDO tag(Long id, Long userId, AuditStatus status) {
        TagDO tag = new TagDO();
        tag.setId(id);
        tag.setUserId(userId);
        tag.setAuditStatus(status.getCode());
        return tag;
    }
}
