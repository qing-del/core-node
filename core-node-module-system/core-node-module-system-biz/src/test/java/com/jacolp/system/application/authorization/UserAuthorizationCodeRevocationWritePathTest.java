package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.security.activation.AccountVerificationCredentialService;
import com.jacolp.common.messaging.pulisher.UserProfileEventPublisher;
import com.jacolp.system.application.dto.user.UserProfileUpdateDTO;
import com.jacolp.system.application.service.impl.UserUserServiceImpl;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAuthorizationCodeRevocationWritePathTest {

    @AfterEach
    void clearContext() {
        TestSecurityContext.clear();
    }

    @Test
    void profileSecurityFieldChangePublishesBeforeRevocationButNicknameOnlyDoesNotRevoke() {
        UserMapper users = mock(UserMapper.class);
        UserProfileEventPublisher events = mock(UserProfileEventPublisher.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        UserDO user = user(7L, UserConstant.UNACTIVE_STATUS);
        when(users.selectById(7L)).thenReturn(user);
        when(users.updateById(user)).thenReturn(1);
        TestSecurityContext.authenticate(7L, false);
        UserUserServiceImpl service = service(users, Mockito.mock(AccountVerificationCredentialService.class), events, revocation);

        UserProfileUpdateDTO emailChange = new UserProfileUpdateDTO();
        emailChange.setEmail("new@example.com");
        service.updateCurrentUserProfile(emailChange);

        InOrder ordered = inOrder(users, events, revocation);
        ordered.verify(users).updateById(user);
        ordered.verify(events).publish(any());
        ordered.verify(revocation).revokeForSecurityFieldChange(7L);

        UserProfileUpdateDTO nicknameOnly = new UserProfileUpdateDTO();
        nicknameOnly.setNickname("new nickname");
        service.updateCurrentUserProfile(nicknameOnly);
        verify(revocation).revokeForSecurityFieldChange(7L);
    }

    @Test
    void softDeleteAndDirectActivationRevokeOnlyAfterTheirDatabaseUpdates() {
        UserMapper users = mock(UserMapper.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        UserDO deleted = user(8L, UserConstant.ACTIVE_STATUS);
        when(users.selectById(8L)).thenReturn(deleted);
        when(users.updateById(any(UserDO.class))).thenReturn(1);
        TestSecurityContext.authenticate(8L, false);
        UserUserServiceImpl service = service(users, mock(AccountVerificationCredentialService.class),
                mock(UserProfileEventPublisher.class), revocation);

        service.deleteCurrentUser();

        InOrder deleteOrder = inOrder(users, revocation);
        deleteOrder.verify(users).updateById(any(UserDO.class));
        deleteOrder.verify(revocation).revokeForSecurityFieldChange(8L);

        UserDO inactive = user(9L, UserConstant.UNACTIVE_STATUS);
        when(users.selectById(9L)).thenReturn(inactive);
        service.activeAccount(9L);

        InOrder activationOrder = inOrder(users, revocation);
        activationOrder.verify(users).updateById(inactive);
        activationOrder.verify(revocation).revokeForSecurityFieldChange(9L);
    }

    @Test
    void activationAndEmailVerificationCompleteOtherBusinessOperationsBeforeRevocation() {
        UserMapper users = mock(UserMapper.class);
        AccountVerificationCredentialService credentials = mock(AccountVerificationCredentialService.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        UserDO inactive = user(10L, UserConstant.UNACTIVE_STATUS);
        when(users.selectById(10L)).thenReturn(inactive);
        when(users.updateById(any(UserDO.class))).thenReturn(1);
        when(credentials.findActivationCodeUserId("activate")).thenReturn(10L);
        UserUserServiceImpl service = service(users, credentials,
                mock(UserProfileEventPublisher.class), revocation);

        service.verifyActivationCode("activate");

        InOrder activationOrder = inOrder(users, credentials, revocation);
        activationOrder.verify(users).updateById(inactive);
        activationOrder.verify(credentials).deleteActivationCode("activate");
        activationOrder.verify(revocation).revokeForSecurityFieldChange(10L);

        when(credentials.findEmailChangeCode("email"))
                .thenReturn(new AccountVerificationCredentialService.EmailChangeCode(11L, "new@example.com"));
        TestSecurityContext.authenticate(11L, false);
        service.verifyEmailChangeCode("email");

        InOrder emailOrder = inOrder(users, credentials, revocation);
        emailOrder.verify(users).updateById(any(UserDO.class));
        emailOrder.verify(credentials).deleteEmailChangeCode("email");
        emailOrder.verify(revocation).revokeForSecurityFieldChange(11L);
    }

    @Test
    void revocationFailureIsNotCaughtAfterAUserSecurityFieldUpdate() {
        UserMapper users = mock(UserMapper.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        UserDO user = user(12L, UserConstant.ACTIVE_STATUS);
        when(users.selectById(12L)).thenReturn(user);
        when(users.updateById(any(UserDO.class))).thenReturn(1);
        RuntimeException failure = new IllegalStateException("redis unavailable");
        org.mockito.Mockito.doThrow(failure).when(revocation).revokeForSecurityFieldChange(12L);
        TestSecurityContext.authenticate(12L, false);
        UserUserServiceImpl service = service(users, mock(AccountVerificationCredentialService.class),
                mock(UserProfileEventPublisher.class), revocation);

        org.assertj.core.api.Assertions.assertThatThrownBy(service::deleteCurrentUser).isSameAs(failure);
        verify(revocation).revokeForSecurityFieldChange(12L);
        verify(users).updateById(any(UserDO.class));
    }

    private static UserUserServiceImpl service(UserMapper users, AccountVerificationCredentialService credentials,
                                               UserProfileEventPublisher events,
                                               AccountAuthorizationStateRevocationService revocation) {
        UserUserServiceImpl service = new UserUserServiceImpl(revocation);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "accountVerificationCredentialService", credentials);
        ReflectionTestUtils.setField(service, "userProfileEvents", events);
        ReflectionTestUtils.setField(service, "passwordEncoder", Mockito.mock(PasswordEncoder.class));
        return service;
    }

    private static UserDO user(Long id, Integer status) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname("User " + id);
        user.setEmail("old@example.com");
        user.setRoleId(RoleConstant.USER);
        user.setStatus(status);
        user.setPassword("hash");
        return user;
    }
}
