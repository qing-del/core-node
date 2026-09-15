package com.jacolp.system.api;

import java.util.Collection;
import java.util.Map;

/**
 * Cross-module read contract for the user information needed to display a business record.
 * Implementations must perform one batch lookup and omit unknown user ids from the result.
 */
public interface UserProfileApi {

    Map<Long, UserProfile> getProfilesByIds(Collection<Long> userIds);

    record UserProfile(long userId, String username, String nickname) {

        public UserProfile {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
        }
    }
}
