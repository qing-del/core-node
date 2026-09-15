package com.jacolp.system.application.authorization;

import com.jacolp.system.constant.RoleConstant;

/**
 * New accounts have no explicit account grant additions. Default account grants are
 * supplied by common-security configuration, while this policy keeps role-directory
 * validation at all existing account write paths.
 */
public final class UserExtraGrantTypePolicy {

    private static final String NO_EXTRA_GRANT_TYPES = "";

    private UserExtraGrantTypePolicy() {
    }

    public static String forRoleId(Long roleId) {
        if (Long.valueOf(RoleConstant.USER).equals(roleId)
                || Long.valueOf(RoleConstant.ADMIN).equals(roleId)
                || Long.valueOf(RoleConstant.CREATOR).equals(roleId)) {
            return NO_EXTRA_GRANT_TYPES;
        }
        throw new IllegalArgumentException("No account extra grant metadata is defined for roleId=" + roleId);
    }
}
