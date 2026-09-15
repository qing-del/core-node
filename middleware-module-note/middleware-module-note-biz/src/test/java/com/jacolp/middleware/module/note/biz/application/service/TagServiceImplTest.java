package com.jacolp.middleware.module.note.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.jacolp.module.note.biz.application.service.TagServiceImpl;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TagDO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TagMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class TagServiceImplTest {

    @AfterEach
    void clearContexts() {
        BaseContext.remove();
        PermissionContext.remove();
    }

    @Test
    void submitAuditUsesCasThenCreatesSynchronousApplication() {
        TagMapper mapper = mock(TagMapper.class);
        AuditApplicationApi auditApi = mock(AuditApplicationApi.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.WAIT));
        when(mapper.updateAuditStatusIfCurrent(7L, AuditStatus.WAIT.getCode(),
                AuditStatus.AUDITING.getCode())).thenReturn(1);

        service(mapper, auditApi).submitTagAudit(7L);

        ArgumentCaptor<CreateAuditApplicationCommand> command = ArgumentCaptor.forClass(CreateAuditApplicationCommand.class);
        verify(auditApi).createApplication(command.capture());
        assertEquals(AuditTargetType.TAG, command.getValue().targetType());
        assertEquals("tag-7", command.getValue().targetName());
    }

    @Test
    void submitAuditDoesNotCreateApplicationWhenCasFails() {
        TagMapper mapper = mock(TagMapper.class);
        AuditApplicationApi auditApi = mock(AuditApplicationApi.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.WAIT));
        when(mapper.updateAuditStatusIfCurrent(7L, AuditStatus.WAIT.getCode(),
                AuditStatus.AUDITING.getCode())).thenReturn(0);

        assertThrows(BaseException.class, () -> service(mapper, auditApi).submitTagAudit(7L));

        verify(auditApi, never()).createApplication(any());
    }

    @Test
    void cancelAuditCancelsApplicationThenUsesCasToRestoreWaiting() {
        TagMapper mapper = mock(TagMapper.class);
        AuditApplicationApi auditApi = mock(AuditApplicationApi.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.AUDITING));
        when(mapper.updateAuditStatusIfCurrent(7L, AuditStatus.AUDITING.getCode(),
                AuditStatus.WAIT.getCode())).thenReturn(1);

        service(mapper, auditApi).cancelTagAudit(7L);

        ArgumentCaptor<CancelAuditApplicationCommand> command = ArgumentCaptor.forClass(CancelAuditApplicationCommand.class);
        verify(auditApi).cancelApplication(command.capture());
        assertEquals(AuditTargetType.TAG, command.getValue().targetType());
        assertEquals(9L, command.getValue().actorUserId());
    }

    private static TagServiceImpl service(TagMapper mapper, AuditApplicationApi auditApi) {
        TagServiceImpl service = new TagServiceImpl();
        ReflectionTestUtils.setField(service, "tagMapper", mapper);
        ReflectionTestUtils.setField(service, "auditApi", auditApi);
        return service;
    }

    private static TagDO tag(Long id, Long userId, AuditStatus status) {
        TagDO tag = new TagDO();
        tag.setId(id);
        tag.setUserId(userId);
        tag.setTagName("tag-" + id);
        tag.setAuditStatus(status.getCode());
        return tag;
    }
}
