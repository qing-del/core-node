package com.jacolp.system.infrastructure.persistence.repository;

import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MyBatis-backed read adapter for authorization account metadata.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisAuthorizationAccountRepository implements AuthorizationAccountRepository {

    private final UserMapper userMapper;

    @Override
    public Optional<AuthorizationAccount> findById(Long userId) {
        return userId == null ? Optional.empty() : Optional.ofNullable(userMapper.selectById(userId))
                .map(MyBatisAuthorizationAccountRepository::toAuthorizationAccount);
    }

    @Override
    public Optional<AuthorizationAccount> findByUsername(String username) {
        return username == null ? Optional.empty() : Optional.ofNullable(userMapper.selectByUsername(username))
                .map(MyBatisAuthorizationAccountRepository::toAuthorizationAccount);
    }

    @Override
    public Optional<AuthorizationAccount> findByEmail(String email) {
        return email == null ? Optional.empty() : Optional.ofNullable(userMapper.selectByEmail(email))
                .map(MyBatisAuthorizationAccountRepository::toAuthorizationAccount);
    }

    private static AuthorizationAccount toAuthorizationAccount(UserDO user) {
        return new AuthorizationAccount(user.getId(), user.getUsername(), user.getPassword(), user.getEmail(),
                user.getRoleId(), user.getExtraGrantTypes(), user.getStatus());
    }
}
