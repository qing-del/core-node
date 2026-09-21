package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.system.constant.RoleConstant;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.system.infrastructure.security.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreatorAccountSynchronizationServiceTest {

    @Test
    void newCreatorAccountDoesNotRevokeAnAuthorizationCode() {
        UserMapper users = mock(UserMapper.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        when(users.selectById(1L)).thenReturn(null);
        when(users.selectByUsername("creator")).thenReturn(null);
        when(passwords.encode("secret")).thenReturn("hash");
        when(users.upsertCreator(any(UserDO.class))).thenReturn(1);

        service(users, passwords, revocation).synchronize("creator", "secret", "creator@example.com", 1024L);

        verify(users).upsertCreator(any(UserDO.class));
        verifyNoInteractions(revocation);
    }

    @Test
    void unchangedExistingCreatorDoesNotRevokeAndPreservesItsPasswordHash() {
        UserMapper users = mock(UserMapper.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        UserDO existing = creator("creator", "hash", "creator@example.com");
        when(users.selectById(1L)).thenReturn(existing);
        when(passwords.matches("secret", "hash")).thenReturn(true);
        when(users.upsertCreator(any(UserDO.class))).thenReturn(1);

        service(users, passwords, revocation).synchronize("creator", "secret", "creator@example.com", 1024L);

        verifyNoInteractions(revocation);
        verify(passwords).matches("secret", "hash");
    }

    @Test
    void changedExistingCreatorUpsertsBeforeTheFinalRevocation() {
        UserMapper users = mock(UserMapper.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        UserDO existing = creator("creator", "hash", "old@example.com");
        when(users.selectById(1L)).thenReturn(existing);
        when(passwords.matches("secret", "hash")).thenReturn(true);
        when(users.upsertCreator(any(UserDO.class))).thenReturn(1);

        service(users, passwords, revocation).synchronize("creator", "secret", "creator@example.com", 1024L);

        InOrder order = inOrder(users, revocation);
        order.verify(users).upsertCreator(any(UserDO.class));
        order.verify(revocation).revokeForSecurityFieldChange(1L);
    }

    @Test
    void revocationFailurePropagatesAfterTheUpsert() {
        UserMapper users = mock(UserMapper.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        AccountAuthorizationStateRevocationService revocation = Mockito.mock(AccountAuthorizationStateRevocationService.class);
        when(users.selectById(1L)).thenReturn(creator("creator", "hash", "old@example.com"));
        when(passwords.matches("secret", "hash")).thenReturn(true);
        when(users.upsertCreator(any(UserDO.class))).thenReturn(1);
        RuntimeException failure = new IllegalStateException("redis unavailable");
        org.mockito.Mockito.doThrow(failure).when(revocation).revokeForSecurityFieldChange(1L);

        assertThatThrownBy(() -> service(users, passwords, revocation)
                .synchronize("creator", "secret", "creator@example.com", 1024L)).isSameAs(failure);
        verify(users).upsertCreator(any(UserDO.class));
    }

    @Test
    void realSpringTransactionalProxyCommitsOnlyAfterTheFinalRevocation() {
        new ApplicationContextRunner().withUserConfiguration(TransactionConfig.class).run(context -> {
            CreatorAccountSynchronizationService service = context.getBean(CreatorAccountSynchronizationService.class);
            UserMapper users = context.getBean(UserMapper.class);
            PasswordEncoder passwords = context.getBean(PasswordEncoder.class);
            AccountAuthorizationStateRevocationService revocation =
                    context.getBean(AccountAuthorizationStateRevocationService.class);
            PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
            when(users.selectById(1L)).thenReturn(creator("creator", "hash", "old@example.com"));
            when(passwords.matches("secret", "hash")).thenReturn(true);
            when(users.upsertCreator(any(UserDO.class))).thenReturn(1);

            service.synchronize("creator", "secret", "creator@example.com", 1024L);

            assertThat(AopUtils.isAopProxy(service)).isTrue();
            InOrder order = inOrder(transactions, users, revocation);
            order.verify(transactions).getTransaction(any());
            order.verify(users).upsertCreator(any(UserDO.class));
            order.verify(revocation).revokeForSecurityFieldChange(1L);
            order.verify(transactions).commit(any());
        });
    }

    private static CreatorAccountSynchronizationService service(UserMapper users, PasswordEncoder passwords,
                                                                 AccountAuthorizationStateRevocationService revocation) {
        return new CreatorAccountSynchronizationService(users, passwords, revocation);
    }

    private static UserDO creator(String username, String password, String email) {
        UserDO user = new UserDO();
        user.setId(1L);
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setRoleId(RoleConstant.CREATOR);
        user.setExtraGrantTypes("");
        user.setStatus(UserConstant.ACTIVE_STATUS);
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfig {
        @Bean UserMapper userMapper() { return mock(UserMapper.class); }
        @Bean PasswordEncoder passwordEncoder() { return mock(PasswordEncoder.class); }
        @Bean AccountAuthorizationStateRevocationService authorizationStateRevocationService() {
            return Mockito.mock(AccountAuthorizationStateRevocationService.class);
        }
        @Bean PlatformTransactionManager transactionManager() {
            PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            return manager;
        }
        @Bean CreatorAccountSynchronizationService creatorAccountSynchronizationService(UserMapper users,
                                                                                         PasswordEncoder passwords,
                                                                                         AccountAuthorizationStateRevocationService revocation) {
            return new CreatorAccountSynchronizationService(users, passwords, revocation);
        }
    }
}
