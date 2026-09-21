package com.jacolp.system.application.authorization;

import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.system.infrastructure.security.PasswordEncoder;
import com.jacolp.system.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Synchronizes the system-managed ID=1 creator account inside a real Spring transaction.
 *
 * <p>The authorization-code revocation is deliberately the final operation: a Redis failure propagates and rolls the
 * creator upsert back. If the database commit itself subsequently fails, the already revoked code remains invalid,
 * which is the accepted fail-safe outcome for this first release.</p>
 */
@Service
public class CreatorAccountSynchronizationService {

    private static final long CREATOR_USER_ID = 1L;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountAuthorizationStateRevocationService authorizationStateRevocationService;

    public CreatorAccountSynchronizationService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                                                AccountAuthorizationStateRevocationService authorizationStateRevocationService) {
        this.userMapper = Objects.requireNonNull(userMapper, "userMapper");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.authorizationStateRevocationService = Objects.requireNonNull(
                authorizationStateRevocationService, "authorizationStateRevocationService");
    }

    @Transactional(rollbackFor = Exception.class)
    public void synchronize(String username, String rawPassword, String email, Long maxStorageBytes) {
        requireText(username, "username");
        requireText(rawPassword, "rawPassword");
        requireText(email, "email");
        Objects.requireNonNull(maxStorageBytes, "maxStorageBytes");

        UserDO existing = userMapper.selectById(CREATOR_USER_ID);
        if (existing == null) {
            existing = userMapper.selectByUsername(username);
        }

        UserDO creator = desiredCreator(username, rawPassword, email, maxStorageBytes, existing);
        int count = userMapper.upsertCreator(creator);
        if (count <= 0) {
            throw new IllegalStateException("creator account upsert failed");
        }
        if (existing != null && securityFieldsChanged(existing, creator)) {
            authorizationStateRevocationService.revokeForSecurityFieldChange(CREATOR_USER_ID);
        }
    }

    private UserDO desiredCreator(String username, String rawPassword, String email, Long maxStorageBytes,
                                  UserDO existing) {
        UserDO creator = new UserDO();
        creator.setId(CREATOR_USER_ID);
        creator.setUsername(username);
        creator.setPassword(existing != null && passwordEncoder.matches(rawPassword, existing.getPassword())
                ? existing.getPassword() : passwordEncoder.encode(rawPassword));
        creator.setEmail(email);
        creator.setRoleId(RoleConstant.CREATOR);
        creator.setExtraGrantTypes(UserExtraGrantTypePolicy.forRoleId(creator.getRoleId()));
        creator.setStatus(UserConstant.ACTIVE_STATUS);
        creator.setMaxStorageBytes(maxStorageBytes);
        return creator;
    }

    private static boolean securityFieldsChanged(UserDO existing, UserDO desired) {
        return !Objects.equals(existing.getUsername(), desired.getUsername())
                || !Objects.equals(existing.getPassword(), desired.getPassword())
                || !Objects.equals(existing.getEmail(), desired.getEmail())
                || !Objects.equals(existing.getRoleId(), desired.getRoleId())
                || !Objects.equals(existing.getExtraGrantTypes(), desired.getExtraGrantTypes())
                || !Objects.equals(existing.getStatus(), desired.getStatus());
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
