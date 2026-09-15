package com.jacolp.module.system.biz.application.api;

import com.jacolp.module.system.api.UserProfileApi;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Batch user-profile reader owned by the system module.
 */
@Service
public class UserProfileApiService implements UserProfileApi {

    private final UserMapper userMapper;

    public UserProfileApiService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Map<Long, UserProfile> getProfilesByIds(Collection<Long> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");
        List<Long> ids = userIds.stream()
                .peek(id -> {
                    if (id == null || id <= 0) {
                        throw new IllegalArgumentException("userIds must contain positive ids only");
                    }
                })
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, UserProfile> profiles = new LinkedHashMap<>();
        for (UserDO user : userMapper.selectByIds(ids)) {
            profiles.put(user.getId(), new UserProfile(user.getId(), user.getUsername(), user.getNickname()));
        }
        return Map.copyOf(profiles);
    }
}
