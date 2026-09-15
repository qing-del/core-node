package com.jacolp.middleware.module.system.biz.application.service.impl;

import com.jacolp.constant.RoleConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.middleware.messaging.event.UserProfileChangedEvent;
import com.jacolp.middleware.messaging.pulisher.UserProfileEventPublisher;
import com.jacolp.module.system.biz.application.dto.user.UserAddDTO;
import com.jacolp.module.system.biz.application.dto.user.UserProfileUpdateDTO;
import com.jacolp.module.system.biz.application.service.impl.AdminUserServiceImpl;
import com.jacolp.module.system.biz.application.service.impl.UserUserServiceImpl;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.infrastructure.security.PasswordEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileProjectionPublishingTest {

    @AfterEach
    void clearContext() {
        BaseContext.remove();
    }

    @Test
    void profileUpdatePublishesCompleteCurrentSnapshot() {
        UserMapper users = mock(UserMapper.class);
        UserProfileEventPublisher events = mock(UserProfileEventPublisher.class);
        UserDO user = user(7L, "alice", "old-name");
        when(users.selectById(7L)).thenReturn(user);
        when(users.updateById(user)).thenReturn(1);
        UserUserServiceImpl service = userService(users, events);
        UserProfileUpdateDTO update = new UserProfileUpdateDTO();
        update.setNickname("new-name");
        BaseContext.setCurrentId(7L);

        service.updateCurrentUserProfile(update);

        ArgumentCaptor<UserProfileChangedEvent> event = ArgumentCaptor.forClass(UserProfileChangedEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue()).isEqualTo(new UserProfileChangedEvent(7L, "alice", "new-name"));
    }

    @Test
    void adminCreationPublishesGeneratedUserSnapshot() {
        UserMapper users = mock(UserMapper.class);
        UserProfileEventPublisher events = mock(UserProfileEventPublisher.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserDO modifier = user(1L, "creator", "Creator");
        modifier.setRoleId(RoleConstant.CREATOR);
        when(users.selectById(1L)).thenReturn(modifier);
        when(users.selectByUsername("alice")).thenReturn(null);
        when(passwords.encode("secret12")).thenReturn("encoded");
        when(users.insertUser(any(UserDO.class))).thenAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(8L);
            return 1;
        });
        AdminUserServiceImpl service = adminService(users, passwords, events);
        UserAddDTO request = new UserAddDTO();
        request.setUsername("alice");
        request.setPassword("secret12");
        request.setNickname("Alice");
        request.setRoleId(RoleConstant.USER);
        BaseContext.setCurrentId(1L);

        service.addUser(request);

        ArgumentCaptor<UserProfileChangedEvent> event = ArgumentCaptor.forClass(UserProfileChangedEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue()).isEqualTo(new UserProfileChangedEvent(8L, "alice", "Alice"));
    }

    private static UserUserServiceImpl userService(UserMapper users, UserProfileEventPublisher events) {
        UserUserServiceImpl service = new UserUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "userProfileEvents", events);
        return service;
    }

    private static AdminUserServiceImpl adminService(UserMapper users, PasswordEncoder passwords,
                                                     UserProfileEventPublisher events) {
        AdminUserServiceImpl service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwords);
        ReflectionTestUtils.setField(service, "userProfileEvents", events);
        return service;
    }

    private static UserDO user(long id, String username, String nickname) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        return user;
    }
}
