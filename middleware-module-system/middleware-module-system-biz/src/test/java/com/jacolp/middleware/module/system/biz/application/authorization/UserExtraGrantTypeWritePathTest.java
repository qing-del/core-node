package com.jacolp.middleware.module.system.biz.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.security.activation.AccountVerificationCredentialService;
import com.jacolp.common.messaging.pulisher.UserProfileEventPublisher;
import com.jacolp.system.application.authorization.AccountAuthorizationStateRevocationService;
import com.jacolp.system.application.authorization.CreatorAccountSynchronizationService;
import com.jacolp.system.application.authorization.RoleRankAuthorizationService;
import com.jacolp.system.application.authorization.UserExtraGrantTypePolicy;
import com.jacolp.system.application.dto.user.UserAddDTO;
import com.jacolp.system.application.dto.user.UserModifyDTO;
import com.jacolp.system.application.dto.user.UserRegisterDTO;
import com.jacolp.system.application.service.EmailSenderService;
import com.jacolp.system.application.service.impl.AdminUserServiceImpl;
import com.jacolp.system.application.service.impl.UserUserServiceImpl;
import com.jacolp.system.constant.RoleConstant;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.system.infrastructure.security.PasswordEncoder;
import com.jacolp.system.support.TestSecurityContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserExtraGrantTypeWritePathTest {

    @AfterEach
    void clearContext() {
        TestSecurityContext.clear();
    }

    @Test
    void roleDirectoryPolicyWritesNoExtraGrantForTheThreeSupportedRoles() {
        Assertions.assertThat(UserExtraGrantTypePolicy.forRoleId(RoleConstant.USER)).isEmpty();
        assertThat(UserExtraGrantTypePolicy.forRoleId(RoleConstant.ADMIN)).isEmpty();
        assertThat(UserExtraGrantTypePolicy.forRoleId(RoleConstant.CREATOR)).isEmpty();
        assertThatThrownBy(() -> UserExtraGrantTypePolicy.forRoleId(99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserExtraGrantTypePolicy.forRoleId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userRegistrationWritesNoExplicitExtraGrant() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AccountVerificationCredentialService credentials = mock(AccountVerificationCredentialService.class);
        AtomicReference<UserDO> inserted = new AtomicReference<>();
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("hash");
        doAnswer(invocation -> {
            UserDO user = invocation.getArgument(0);
            user.setId(7L);
            inserted.set(user);
            return 1;
        }).when(userMapper).insertUser(any(UserDO.class));
        when(userMapper.selectById(7L)).thenAnswer(invocation -> inserted.get());
        when(credentials.acquireActivationEmailCooldown(7L)).thenReturn(true);

        UserUserServiceImpl service = new UserUserServiceImpl(Mockito.mock(AccountAuthorizationStateRevocationService.class));
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "accountVerificationCredentialService", credentials);
        ReflectionTestUtils.setField(service, "emailSenderService", Mockito.mock(EmailSenderService.class));
        ReflectionTestUtils.setField(service, "userProfileEvents", Mockito.mock(UserProfileEventPublisher.class));
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("password");
        dto.setConfirmPassword("password");
        dto.setEmail("alice@example.com");

        service.register(dto);

        assertThat(inserted.get().getRoleId()).isEqualTo(RoleConstant.USER);
        assertThat(inserted.get().getExtraGrantTypes()).isEmpty();
    }

    @Test
    void adminCreateAndRoleChangeExplicitlyClearExtraGrantsForTheirTargetRole() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserProfileEventPublisher userProfileEvents = mock(UserProfileEventPublisher.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl(mock(AccountAuthorizationStateRevocationService.class));
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "userProfileEvents", userProfileEvents);
        ReflectionTestUtils.setField(service, "roleRankAuthorizationService", Mockito.mock(RoleRankAuthorizationService.class));
        TestSecurityContext.authenticate(1L, true);
        UserDO creator = user(1L, RoleConstant.CREATOR);
        when(userMapper.selectById(1L)).thenReturn(creator);
        when(userMapper.selectByUsername("admin2")).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("hash");
        doAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(2L);
            return 1;
        }).when(userMapper).insertUser(any(UserDO.class));
        UserAddDTO add = new UserAddDTO();
        add.setUsername("admin2");
        add.setPassword("password");
        add.setRoleId(RoleConstant.ADMIN);
        add.setEmail("admin2@example.com");

        service.addUser(add);

        ArgumentCaptor<UserDO> inserted = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insertUser(inserted.capture());
        assertThat(inserted.getValue().getExtraGrantTypes()).isEmpty();

        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(2L)).thenReturn(user(2L, RoleConstant.USER));
        UserModifyDTO modify = new UserModifyDTO();
        modify.setId(2L);
        modify.setRoleId(RoleConstant.USER);

        service.modifyUser(modify);

        ArgumentCaptor<UserDO> updated = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).updateById(updated.capture());
        assertThat(updated.getValue().getExtraGrantTypes()).isEmpty();
    }

    @Test
    void creatorSynchronizationWritesNoExplicitExtraGrantForANewAccount() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userMapper.selectById(1L)).thenReturn(null);
        when(userMapper.selectByUsername("creator")).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("hash");
        when(userMapper.upsertCreator(any(UserDO.class))).thenReturn(1);
        CreatorAccountSynchronizationService service = new CreatorAccountSynchronizationService(userMapper,
                passwordEncoder, mock(AccountAuthorizationStateRevocationService.class));

        service.synchronize("creator", "password", "creator@example.com", 1024L);

        ArgumentCaptor<UserDO> creator = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).upsertCreator(creator.capture());
        assertThat(creator.getValue().getRoleId()).isEqualTo(RoleConstant.CREATOR);
        assertThat(creator.getValue().getExtraGrantTypes()).isEmpty();
    }

    private static UserDO user(Long id, Long roleId) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRoleId(roleId);
        user.setStatus(UserConstant.ACTIVE_STATUS);
        return user;
    }
}
