package com.jacolp.middleware.module.system.biz.application.service.impl;

import com.jacolp.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.middleware.common.security.token.TokenSessionService;
import com.jacolp.module.system.biz.application.dto.user.UserLoginDTO;
import com.jacolp.module.system.biz.application.service.impl.AdminUserServiceImpl;
import com.jacolp.module.system.biz.application.service.impl.UserUserServiceImpl;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.infrastructure.security.PasswordEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TokenSessionWorkflowTest {
    @AfterEach void clear() { BaseContext.remove(); }

    @Test void userLoginIssuesOnlyAfterPasswordValidationAndLogoutRevokesCurrentId() {
        UserMapper mapper = mock(UserMapper.class); TokenSessionService tokens = mock(TokenSessionService.class); PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserUserServiceImpl service = user(mapper, tokens, passwords); UserDO user = user(1L, UserConstant.ACTIVE_STATUS, RoleConstant.USER);
        when(mapper.selectByUsername("user")).thenReturn(user); when(passwords.matches("pw", "hash")).thenReturn(true); when(tokens.issueUserLoginToken(1L)).thenReturn("jwt");
        assertThat(service.loginUser(new UserLoginDTO("user", "pw"))).isEqualTo("jwt");
        InOrder order = inOrder(passwords, tokens); order.verify(passwords).matches("pw", "hash"); order.verify(tokens).issueUserLoginToken(1L);
        BaseContext.setCurrentId(1L); service.logout(); verify(tokens).revokeUserLoginToken(1L);
    }

    @Test void userStatusOrPasswordFailureNeverIssues() {
        UserMapper mapper = mock(UserMapper.class); TokenSessionService tokens = mock(TokenSessionService.class); PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserUserServiceImpl service = user(mapper, tokens, passwords);
        when(mapper.selectByUsername("user")).thenReturn(user(1L, UserConstant.BANNED_STATUS, RoleConstant.USER));
        assertThatThrownBy(() -> service.loginUser(new UserLoginDTO("user", "pw"))).isNotNull(); verifyNoInteractions(tokens);
        when(mapper.selectByUsername("user")).thenReturn(user(1L, UserConstant.ACTIVE_STATUS, RoleConstant.USER)); when(passwords.matches("pw", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.loginUser(new UserLoginDTO("user", "pw"))).isNotNull(); verifyNoInteractions(tokens);
    }

    @Test void adminLoginIssuesAfterRoleAndPasswordAndLogoutRevokes() {
        UserMapper mapper = mock(UserMapper.class); TokenSessionService tokens = mock(TokenSessionService.class); PasswordEncoder passwords = mock(PasswordEncoder.class);
        AdminUserServiceImpl service = admin(mapper, tokens, passwords); UserDO admin = user(2L, UserConstant.ACTIVE_STATUS, RoleConstant.ADMIN);
        when(mapper.selectByUsername("admin")).thenReturn(admin); when(passwords.matches("pw", "hash")).thenReturn(true); when(tokens.issueAdminLoginToken(2L)).thenReturn("admin-jwt");
        assertThat(service.loginAdmin(new UserLoginDTO("admin", "pw"))).isEqualTo("admin-jwt");
        InOrder order = inOrder(passwords, tokens); order.verify(passwords).matches("pw", "hash"); order.verify(tokens).issueAdminLoginToken(2L);
        BaseContext.setCurrentId(2L); service.logout(); verify(tokens).revokeAdminLoginToken(2L);
    }

    @Test void adminRoleOrPasswordFailureNeverIssues() {
        UserMapper mapper = mock(UserMapper.class); TokenSessionService tokens = mock(TokenSessionService.class); PasswordEncoder passwords = mock(PasswordEncoder.class);
        AdminUserServiceImpl service = admin(mapper, tokens, passwords);
        when(mapper.selectByUsername("admin")).thenReturn(user(2L, UserConstant.ACTIVE_STATUS, RoleConstant.USER));
        assertThatThrownBy(() -> service.loginAdmin(new UserLoginDTO("admin", "pw"))).isNotNull(); verifyNoInteractions(tokens);
        when(mapper.selectByUsername("admin")).thenReturn(user(2L, UserConstant.ACTIVE_STATUS, RoleConstant.ADMIN)); when(passwords.matches("pw", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.loginAdmin(new UserLoginDTO("admin", "pw"))).isNotNull(); verifyNoInteractions(tokens);
    }

    @Test void activationCodeDeletesOnlyAfterAccountActivationSucceeds() {
        UserMapper mapper = mock(UserMapper.class);
        TokenSessionService tokens = mock(TokenSessionService.class);
        UserUserServiceImpl service = spy(user(mapper, tokens, mock(PasswordEncoder.class)));
        List<String> events = new ArrayList<>();
        when(tokens.findActivationCodeUserId("123456")).thenReturn(3L);
        doAnswer(invocation -> { events.add("activate"); return "账号激活成功"; })
                .when(service).activeAccount(3L);
        doAnswer(invocation -> { events.add("delete"); return null; })
                .when(tokens).deleteActivationCode("123456");

        assertThat(service.verifyActivationCode("123456")).isEqualTo("账号激活成功");
        assertThat(events).containsExactly("activate", "delete");
    }

    @Test void activationCodeIsNotDeletedWhenAccountActivationFails() {
        UserMapper mapper = mock(UserMapper.class);
        TokenSessionService tokens = mock(TokenSessionService.class);
        UserUserServiceImpl service = spy(user(mapper, tokens, mock(PasswordEncoder.class)));
        when(tokens.findActivationCodeUserId("123456")).thenReturn(3L);
        doThrow(new BaseException("激活失败")).when(service).activeAccount(3L);

        assertThatThrownBy(() -> service.verifyActivationCode("123456"))
                .isInstanceOf(BaseException.class)
                .hasMessage("激活失败");
        verify(tokens, never()).deleteActivationCode(anyString());
    }

    @Test void emailChangeCodeDeletesOnlyAfterDatabaseUpdateSucceeds() {
        UserMapper mapper = mock(UserMapper.class);
        TokenSessionService tokens = mock(TokenSessionService.class);
        UserUserServiceImpl service = user(mapper, tokens, mock(PasswordEncoder.class));
        List<String> events = new ArrayList<>();
        when(tokens.findEmailChangeCode("654321"))
                .thenReturn(new TokenSessionService.EmailChangeCode(4L, "new@test.com"));
        when(mapper.updateById(any(UserDO.class))).thenAnswer(invocation -> { events.add("update"); return 1; });
        doAnswer(invocation -> { events.add("delete"); return null; })
                .when(tokens).deleteEmailChangeCode("654321");
        BaseContext.setCurrentId(4L);

        assertThat(service.verifyEmailChangeCode("654321")).isEqualTo("邮箱修改成功");
        assertThat(events).containsExactly("update", "delete");
    }

    @Test void emailChangeCodeIsNotDeletedForOwnerMismatchOrDatabaseFailure() {
        TokenSessionService mismatchTokens = mock(TokenSessionService.class);
        UserMapper mismatchMapper = mock(UserMapper.class);
        UserUserServiceImpl mismatchService = user(mismatchMapper, mismatchTokens, mock(PasswordEncoder.class));
        when(mismatchTokens.findEmailChangeCode("owner"))
                .thenReturn(new TokenSessionService.EmailChangeCode(5L, "new@test.com"));
        BaseContext.setCurrentId(6L);
        assertThatThrownBy(() -> mismatchService.verifyEmailChangeCode("owner"))
                .isInstanceOf(BaseException.class)
                .hasMessage("验证码无效或已过期");
        verify(mismatchMapper, never()).updateById(any());
        verify(mismatchTokens, never()).deleteEmailChangeCode(anyString());

        BaseContext.setCurrentId(5L);
        TokenSessionService failedTokens = mock(TokenSessionService.class);
        UserMapper failedMapper = mock(UserMapper.class);
        UserUserServiceImpl failedService = user(failedMapper, failedTokens, mock(PasswordEncoder.class));
        when(failedTokens.findEmailChangeCode("database"))
                .thenReturn(new TokenSessionService.EmailChangeCode(5L, "new@test.com"));
        when(failedMapper.updateById(any(UserDO.class))).thenReturn(0);
        assertThatThrownBy(() -> failedService.verifyEmailChangeCode("database"))
                .isInstanceOf(BaseException.class)
                .hasMessage(UserConstant.UPDATE_USER_INFO_FAILED);
        verify(failedTokens, never()).deleteEmailChangeCode(anyString());
    }

    private static UserUserServiceImpl user(UserMapper mapper, TokenSessionService tokens, PasswordEncoder passwords) {
        UserUserServiceImpl service = new UserUserServiceImpl(); ReflectionTestUtils.setField(service, "userMapper", mapper); ReflectionTestUtils.setField(service, "tokenSessionService", tokens); ReflectionTestUtils.setField(service, "passwordEncoder", passwords); return service;
    }
    private static AdminUserServiceImpl admin(UserMapper mapper, TokenSessionService tokens, PasswordEncoder passwords) {
        AdminUserServiceImpl service = new AdminUserServiceImpl(); ReflectionTestUtils.setField(service, "userMapper", mapper); ReflectionTestUtils.setField(service, "tokenSessionService", tokens); ReflectionTestUtils.setField(service, "passwordEncoder", passwords); return service;
    }
    private static UserDO user(Long id, Integer status, Long role) { UserDO user = new UserDO(); user.setId(id); user.setStatus(status); user.setRoleId(role); user.setPassword("hash"); return user; }
}
