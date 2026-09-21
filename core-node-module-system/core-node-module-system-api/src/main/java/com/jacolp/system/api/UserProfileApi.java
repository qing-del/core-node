package com.jacolp.system.api;

import java.util.Collection;
import java.util.Map;

/**
 * Cross-module read contract for the user information needed to display a business record.
 * Implementations must perform one batch lookup and omit unknown user ids from the result.
 */
public interface UserProfileApi {

    Map<Long, UserProfile> getProfilesByIds(Collection<Long> userIds);

    /**
     * Checks whether an account exists and is currently enabled for business operations.
     *
     * <p>The method deliberately returns {@code false} for both unknown and inactive users
     * so callers do not need to depend on the system module's persistence model.</p>
     */
    boolean isActiveUser(long userId);

    record UserProfile(long userId, String username, String nickname) {

        public UserProfile {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
        }
    }
}
