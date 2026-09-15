package com.jacolp.system.infrastructure.bootstrap;

import com.jacolp.system.application.authorization.CreatorAccountSynchronizationService;
import com.jacolp.system.constant.RoleConstant;
import com.jacolp.system.infrastructure.persistence.mapper.RoleMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataInitializerCreatorSynchronizationTest {

    @Test
    void startupCompletesRoleCacheInitializationBeforeDelegatingCreatorSynchronization() throws Exception {
        RoleMapper roles = mock(RoleMapper.class);
        CreatorAccountSynchronizationService synchronization = mock(CreatorAccountSynchronizationService.class);
        when(roles.getAll()).thenReturn(List.of());
        DataInitializer initializer = new DataInitializer(roles, synchronization);
        ReflectionTestUtils.setField(initializer, "adminUsername", "creator");
        ReflectionTestUtils.setField(initializer, "adminPassword", "secret");
        ReflectionTestUtils.setField(initializer, "adminEmail", "creator@example.com");

        initializer.run();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(roles, synchronization);
        order.verify(roles).getAll();
        order.verify(synchronization).synchronize(eq("creator"), eq("secret"), eq("creator@example.com"),
                ArgumentMatchers.eq(RoleConstant.USER_MAX_STORAGE_BYTES));
    }
}
