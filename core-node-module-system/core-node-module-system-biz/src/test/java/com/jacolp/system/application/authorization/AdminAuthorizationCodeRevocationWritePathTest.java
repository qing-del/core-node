package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.core.exception.PermissionDeniedException;
import com.jacolp.common.messaging.pulisher.UserProfileEventPublisher;
import com.jacolp.system.application.dto.user.UserAddDTO;
import com.jacolp.system.application.dto.user.UserModifyDTO;
import com.jacolp.system.application.service.impl.AdminUserServiceImpl;
import com.jacolp.system.constant.RoleConstant;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.system.infrastructure.security.PasswordEncoder;
import com.jacolp.system.support.TestSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminAuthorizationCodeRevocationWritePathTest {

    @AfterEach
    void clearContext() {
        TestSecurityContext.clear();
    }

    @Test
    void modifyRevokesOnlyWhenSecurityFieldsAreRequestedAfterProfilePublication() {
        UserMapper users = mock(UserMapper.class);
        UserProfileEventPublisher events = mock(UserProfileEventPublisher.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        UserDO creator = user(1L, RoleConstant.CREATOR);
        UserDO target = user(2L, RoleConstant.USER);
        when(users.selectById(1L)).thenReturn(creator);
        when(users.selectById(2L)).thenReturn(target);
        when(users.updateById(any(UserDO.class))).thenReturn(1);
        TestSecurityContext.authenticate(1L, true);
        AdminUserServiceImpl service = service(users, events, Mockito.mock(PasswordEncoder.class), revocation);

        UserModifyDTO nicknameOnly = new UserModifyDTO();
        nicknameOnly.setId(2L);
        nicknameOnly.setNickname("nickname");
        service.modifyUser(nicknameOnly);
        verifyNoInteractions(revocation);

        UserModifyDTO unchangedUsername = new UserModifyDTO();
        unchangedUsername.setId(2L);
        unchangedUsername.setUsername("user2");
        service.modifyUser(unchangedUsername);
        verifyNoInteractions(revocation);

        UserModifyDTO securityChange = new UserModifyDTO();
        securityChange.setId(2L);
        securityChange.setUsername("new_name");
        service.modifyUser(securityChange);

        InOrder order = inOrder(users, events, revocation);
        order.verify(users).updateById(any(UserDO.class));
        order.verify(events).publish(any());
        order.verify(users).updateById(any(UserDO.class));
        order.verify(events).publish(any());
        order.verify(users).updateById(any(UserDO.class));
        order.verify(events).publish(any());
        order.verify(revocation).revokeForSecurityFieldChange(2L);
    }

    @Test
    void newAdminCreatedAccountDoesNotAttemptToRevokeAnAuthorizationCode() {
        UserMapper users = mock(UserMapper.class);
        UserProfileEventPublisher events = mock(UserProfileEventPublisher.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        when(users.selectById(1L)).thenReturn(user(1L, RoleConstant.CREATOR));
        when(users.selectByUsername("new_user")).thenReturn(null);
        when(passwords.encode("secret12")).thenReturn("hash");
        when(users.insertUser(any(UserDO.class))).thenAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(8L);
            return 1;
        });
        TestSecurityContext.authenticate(1L, true);
        AdminUserServiceImpl service = service(users, events, passwords, revocation);
        UserAddDTO add = new UserAddDTO();
        add.setUsername("new_user");
        add.setPassword("secret12");
        add.setRoleId(RoleConstant.USER);

        service.addUser(add);

        verifyNoInteractions(revocation);
    }

    @Test
    void statusUpdateAndBatchDeletionFinishDatabaseWorkBeforeStableDistinctRevocations() {
        UserMapper users = mock(UserMapper.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        UserDO creator = user(1L, RoleConstant.CREATOR);
        UserDO first = user(3L, RoleConstant.USER);
        UserDO second = user(2L, RoleConstant.ADMIN);
        when(users.updateById(any(UserDO.class))).thenReturn(1);
        TestSecurityContext.authenticate(1L, true);
        AdminUserServiceImpl service = service(users, mock(UserProfileEventPublisher.class),
                mock(PasswordEncoder.class), revocation);

        service.updateStatus(7L, UserConstant.BANNED_STATUS);
        InOrder statusOrder = inOrder(users, revocation);
        statusOrder.verify(users).updateById(any(UserDO.class));
        statusOrder.verify(revocation).revokeForSecurityFieldChange(7L);

        when(users.selectById(1L)).thenReturn(creator);
        when(users.selectByIds(List.of(3L, 2L, 3L))).thenReturn(List.of(first, second));
        when(users.deleteByIds(List.of(3L, 2L, 3L))).thenReturn(2);
        service.deleteUsers(List.of(3L, 2L, 3L));

        InOrder deleteOrder = inOrder(users, revocation);
        deleteOrder.verify(users).deleteByIds(List.of(3L, 2L, 3L));
        deleteOrder.verify(revocation).revokeForSecurityFieldChange(3L);
        deleteOrder.verify(revocation).revokeForSecurityFieldChange(2L);
        verify(revocation, never()).revokeForSecurityFieldChange(1L);
    }

    @Test
    void revocationFailureAfterStatusUpdatePropagatesWithoutBeingCaught() {
        UserMapper users = mock(UserMapper.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        when(users.updateById(any(UserDO.class))).thenReturn(1);
        RuntimeException failure = new IllegalStateException("redis unavailable");
        org.mockito.Mockito.doThrow(failure).when(revocation).revokeForSecurityFieldChange(7L);
        AdminUserServiceImpl service = service(users, mock(UserProfileEventPublisher.class),
                mock(PasswordEncoder.class), revocation);

        assertThatThrownBy(() -> service.updateStatus(7L, UserConstant.BANNED_STATUS)).isSameAs(failure);
        verify(users).updateById(any(UserDO.class));
        verify(revocation).revokeForSecurityFieldChange(7L);
    }

    @Test
    void onlyCreatorCanActuallyChangeUsername() {
        UserMapper users = mock(UserMapper.class);
        RoleRankAuthorizationService roleRankAuthorization = Mockito.mock(RoleRankAuthorizationService.class);
        UserDO admin = user(1L, RoleConstant.ADMIN);
        UserDO target = user(2L, RoleConstant.USER);
        when(users.selectById(1L)).thenReturn(admin);
        when(users.selectById(2L)).thenReturn(target);
        TestSecurityContext.authenticate(1L, true);
        AdminUserServiceImpl service = service(users, mock(UserProfileEventPublisher.class),
                mock(PasswordEncoder.class), Mockito.mock(AccountAuthorizationStateRevocationService.class), roleRankAuthorization);
        UserModifyDTO changedUsername = new UserModifyDTO();
        changedUsername.setId(2L);
        changedUsername.setUsername("renamed_user");
        org.mockito.Mockito.doThrow(new PermissionDeniedException("creator required"))
                .when(roleRankAuthorization).requireCreator(RoleConstant.ADMIN);

        assertThatThrownBy(() -> service.modifyUser(changedUsername)).isInstanceOf(PermissionDeniedException.class);
        verify(users, never()).updateById(any(UserDO.class));
        org.mockito.Mockito.clearInvocations(roleRankAuthorization);

        UserModifyDTO unchangedUsername = new UserModifyDTO();
        unchangedUsername.setId(2L);
        unchangedUsername.setUsername(target.getUsername());
        when(users.updateById(any(UserDO.class))).thenReturn(1);
        service.modifyUser(unchangedUsername);
        verify(roleRankAuthorization, never()).requireCreator(RoleConstant.ADMIN);
    }

    private static AdminUserServiceImpl service(UserMapper users, UserProfileEventPublisher events,
                                                PasswordEncoder passwords,
                                                AccountAuthorizationStateRevocationService revocation) {
        return service(users, events, passwords, revocation, Mockito.mock(RoleRankAuthorizationService.class));
    }

    private static AdminUserServiceImpl service(UserMapper users, UserProfileEventPublisher events,
                                                PasswordEncoder passwords,
                                                AccountAuthorizationStateRevocationService revocation,
                                                RoleRankAuthorizationService roleRankAuthorization) {
        AdminUserServiceImpl service = new AdminUserServiceImpl(revocation);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "userProfileEvents", events);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwords);
        ReflectionTestUtils.setField(service, "roleRankAuthorizationService", roleRankAuthorization);
        return service;
    }

    private static UserDO user(Long id, Long roleId) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname("User " + id);
        user.setRoleId(roleId);
        user.setStatus(UserConstant.ACTIVE_STATUS);
        return user;
    }
}
