package com.jacolp.system.application.authorization.model;

import java.util.List;

/**
 * Effective permission codes inherited by one role through rank ordering.
 */
public record EffectiveRolePermissions(
        Long roleId,
        String roleCode,
        Integer roleRank,
        List<String> permissionCodes) {

    public EffectiveRolePermissions {
        permissionCodes = List.copyOf(permissionCodes);
    }
}
