package com.jacolp.middleware.module.system.biz.application.service.impl;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.security.activation.AccountVerificationCredentialService;
import com.jacolp.system.application.authorization.AccountAuthorizationStateRevocationService;
import com.jacolp.system.application.service.impl.UserUserServiceImpl;
import com.jacolp.system.constant.RoleConstant;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.system.support.TestSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountVerificationCredentialWorkflowTest {
    @AfterEach void clear() { TestSecurityContext.clear(); }

    @Test void activationCodeDeletesOnlyAfterAccountActivationSucceeds() {
        UserMapper mapper = mock(UserMapper.class);
        AccountVerificationCredentialService credentials = mock(AccountVerificationCredentialService.class);
        UserUserServiceImpl service = user(mapper, credentials);
        when(credentials.findActivationCodeUserId("123456")).thenReturn(3L);
        when(mapper.selectById(3L)).thenReturn(user(3L, UserConstant.UNACTIVE_STATUS, RoleConstant.USER));
        when(mapper.updateById(any(UserDO.class))).thenReturn(1);

        assertThat(service.verifyActivationCode("123456")).isEqualTo("激活成功");
        InOrder order = inOrder(mapper, credentials);
        order.verify(mapper).updateById(any(UserDO.class));
        order.verify(credentials).deleteActivationCode("123456");
    }

    @Test void activationCodeIsNotDeletedWhenAccountActivationFails() {
        UserMapper mapper = mock(UserMapper.class);
        AccountVerificationCredentialService credentials = mock(AccountVerificationCredentialService.class);
        UserUserServiceImpl service = user(mapper, credentials);
        when(credentials.findActivationCodeUserId("123456")).thenReturn(3L);
        when(mapper.selectById(3L)).thenReturn(user(3L, UserConstant.UNACTIVE_STATUS, RoleConstant.USER));
        when(mapper.updateById(any(UserDO.class))).thenReturn(0);
        assertThatThrownBy(() -> service.verifyActivationCode("123456"))
                .isInstanceOf(BaseException.class)
                .hasMessage(UserConstant.UPDATE_USER_INFO_FAILED);
        verify(credentials, never()).deleteActivationCode(anyString());
    }

    @Test void emailChangeCodeDeletesOnlyAfterDatabaseUpdateSucceeds() {
        UserMapper mapper = mock(UserMapper.class);
        AccountVerificationCredentialService credentials = mock(AccountVerificationCredentialService.class);
        UserUserServiceImpl service = user(mapper, credentials);
        List<String> events = new ArrayList<>();
        when(credentials.findEmailChangeCode("654321"))
                .thenReturn(new AccountVerificationCredentialService.EmailChangeCode(4L, "new@test.com"));
        when(mapper.updateById(any(UserDO.class))).thenAnswer(invocation -> { events.add("update"); return 1; });
        doAnswer(invocation -> { events.add("delete"); return null; })
                .when(credentials).deleteEmailChangeCode("654321");
        TestSecurityContext.authenticate(4L, false);

        assertThat(service.verifyEmailChangeCode("654321")).isEqualTo("邮箱修改成功");
        assertThat(events).containsExactly("update", "delete");
    }

    @Test void emailChangeCodeIsNotDeletedForOwnerMismatchOrDatabaseFailure() {
        AccountVerificationCredentialService mismatchCredentials = mock(AccountVerificationCredentialService.class);
        UserMapper mismatchMapper = mock(UserMapper.class);
        UserUserServiceImpl mismatchService = user(mismatchMapper, mismatchCredentials);
        when(mismatchCredentials.findEmailChangeCode("owner"))
                .thenReturn(new AccountVerificationCredentialService.EmailChangeCode(5L, "new@test.com"));
        TestSecurityContext.authenticate(6L, false);
        assertThatThrownBy(() -> mismatchService.verifyEmailChangeCode("owner"))
                .isInstanceOf(BaseException.class)
                .hasMessage("验证码无效或已过期");
        verify(mismatchMapper, never()).updateById(any());
        verify(mismatchCredentials, never()).deleteEmailChangeCode(anyString());

        TestSecurityContext.authenticate(5L, false);
        AccountVerificationCredentialService failedCredentials = mock(AccountVerificationCredentialService.class);
        UserMapper failedMapper = mock(UserMapper.class);
        UserUserServiceImpl failedService = user(failedMapper, failedCredentials);
        when(failedCredentials.findEmailChangeCode("database"))
                .thenReturn(new AccountVerificationCredentialService.EmailChangeCode(5L, "new@test.com"));
        when(failedMapper.updateById(any(UserDO.class))).thenReturn(0);
        assertThatThrownBy(() -> failedService.verifyEmailChangeCode("database"))
                .isInstanceOf(BaseException.class)
                .hasMessage(UserConstant.UPDATE_USER_INFO_FAILED);
        verify(failedCredentials, never()).deleteEmailChangeCode(anyString());
    }

    private static UserUserServiceImpl user(UserMapper mapper, AccountVerificationCredentialService credentials) {
        UserUserServiceImpl service = new UserUserServiceImpl(Mockito.mock(AccountAuthorizationStateRevocationService.class));
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "accountVerificationCredentialService", credentials);
        return service;
    }
    private static UserDO user(Long id, Integer status, Long role) { UserDO user = new UserDO(); user.setId(id); user.setStatus(status); user.setRoleId(role); user.setPassword("hash"); return user; }
}
