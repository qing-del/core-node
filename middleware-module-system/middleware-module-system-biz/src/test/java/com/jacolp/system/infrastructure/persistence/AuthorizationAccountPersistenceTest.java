package com.jacolp.system.infrastructure.persistence;

import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.system.infrastructure.persistence.repository.MyBatisAuthorizationAccountRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AuthorizationAccountPersistenceTest {

    @Test
    void applicationPortDoesNotExposeInfrastructureTypes() {
        for (Method method : AuthorizationAccountRepository.class.getDeclaredMethods()) {
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain(".infrastructure.persistence.");
            for (java.lang.reflect.Type parameterType : method.getGenericParameterTypes()) {
                assertThat(parameterType.getTypeName()).doesNotContain(".infrastructure.persistence.");
            }
        }
    }

    @Test
    void findByIdMapsAllAuthorizationAccountFieldsAndTreatsMissingRowsAsEmpty() {
        UserMapper mapper = mock(UserMapper.class);
        MyBatisAuthorizationAccountRepository repository = new MyBatisAuthorizationAccountRepository(mapper);
        UserDO user = user(7L, "alice", "alice@example.test");
        when(mapper.selectById(7L)).thenReturn(user);
        when(mapper.selectById(8L)).thenReturn(null);

        Assertions.assertThat(repository.findById(7L)).contains(account(user));
        Assertions.assertThat(repository.findById(8L)).isEmpty();
        Assertions.assertThat(repository.findById(null)).isEmpty();

        verify(mapper).selectById(7L);
        verify(mapper).selectById(8L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void findByUsernameMapsAllAuthorizationAccountFieldsAndTreatsMissingRowsAsEmpty() {
        UserMapper mapper = mock(UserMapper.class);
        MyBatisAuthorizationAccountRepository repository = new MyBatisAuthorizationAccountRepository(mapper);
        UserDO user = user(7L, "alice", "alice@example.test");
        when(mapper.selectByUsername("alice")).thenReturn(user);
        when(mapper.selectByUsername("missing")).thenReturn(null);

        Assertions.assertThat(repository.findByUsername("alice")).contains(account(user));
        Assertions.assertThat(repository.findByUsername("missing")).isEmpty();
        Assertions.assertThat(repository.findByUsername(null)).isEmpty();

        verify(mapper).selectByUsername("alice");
        verify(mapper).selectByUsername("missing");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void findByEmailMapsAllAuthorizationAccountFieldsAndTreatsMissingRowsAsEmpty() {
        UserMapper mapper = mock(UserMapper.class);
        MyBatisAuthorizationAccountRepository repository = new MyBatisAuthorizationAccountRepository(mapper);
        UserDO user = user(7L, "alice", "alice@example.test");
        when(mapper.selectByEmail("alice@example.test")).thenReturn(user);
        when(mapper.selectByEmail("missing@example.test")).thenReturn(null);

        Assertions.assertThat(repository.findByEmail("alice@example.test")).contains(account(user));
        Assertions.assertThat(repository.findByEmail("missing@example.test")).isEmpty();
        Assertions.assertThat(repository.findByEmail(null)).isEmpty();

        verify(mapper).selectByEmail("alice@example.test");
        verify(mapper).selectByEmail("missing@example.test");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void modelAllowsTheSchemaDefaultEmptyExtraGrantTypesButDoesNotLeakPasswordHash() {
        AuthorizationAccount account = new AuthorizationAccount(7L, "alice", "stored-secret", "alice@example.test",
                3L, "", 1);

        assertThat(account.toString()).doesNotContain("stored-secret");
    }

    @Test
    void legacyAccountWithoutAnEmailCanBeBuiltAndMappedWithoutLeakingPasswordHash() {
        AuthorizationAccount account = new AuthorizationAccount(7L, "legacy", "stored-secret", null, 3L, "", 1);
        UserMapper mapper = mock(UserMapper.class);
        MyBatisAuthorizationAccountRepository repository = new MyBatisAuthorizationAccountRepository(mapper);
        UserDO legacyUser = user(7L, "legacy", null);
        when(mapper.selectById(7L)).thenReturn(legacyUser);

        assertThat(account.email()).isNull();
        assertThat(account.toString()).doesNotContain("stored-secret");
        Assertions.assertThat(repository.findById(7L)).contains(account);

        verify(mapper).selectById(7L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void modelRejectsInvalidSecurityFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationAccount(null, "alice", "hash",
                "alice@example.test", 3L, "", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationAccount(7L, " ", "hash",
                "alice@example.test", 3L, "", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationAccount(7L, "alice", " ",
                "alice@example.test", 3L, "", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationAccount(7L, "alice", "hash", " ",
                3L, "", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationAccount(7L, "alice", "hash",
                "alice@example.test", 0L, "", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationAccount(7L, "alice", "hash",
                "alice@example.test", 3L, null, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationAccount(7L, "alice", "hash",
                "alice@example.test", 3L, "", null));
    }

    @Test
    void nullLookupArgumentsDoNotCallMapper() {
        UserMapper mapper = mock(UserMapper.class);
        MyBatisAuthorizationAccountRepository repository = new MyBatisAuthorizationAccountRepository(mapper);

        Assertions.assertThat(repository.findById(null)).isEmpty();
        Assertions.assertThat(repository.findByUsername(null)).isEmpty();
        Assertions.assertThat(repository.findByEmail(null)).isEmpty();

        verifyNoInteractions(mapper);
    }

    @Test
    void mapperFailuresPropagateInsteadOfBeingTreatedAsAMissingAccount() {
        UserMapper mapper = mock(UserMapper.class);
        MyBatisAuthorizationAccountRepository repository = new MyBatisAuthorizationAccountRepository(mapper);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(mapper.selectByUsername("alice")).thenThrow(failure);

        assertThatThrownBy(() -> repository.findByUsername("alice")).isSameAs(failure);

        verify(mapper).selectByUsername("alice");
        verifyNoMoreInteractions(mapper);
    }

    private static UserDO user(Long id, String username, String email) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("stored-secret");
        user.setEmail(email);
        user.setRoleId(3L);
        user.setExtraGrantTypes("");
        user.setStatus(1);
        return user;
    }

    private static AuthorizationAccount account(UserDO user) {
        return new AuthorizationAccount(user.getId(), user.getUsername(), user.getPassword(), user.getEmail(),
                user.getRoleId(), user.getExtraGrantTypes(), user.getStatus());
    }
}
