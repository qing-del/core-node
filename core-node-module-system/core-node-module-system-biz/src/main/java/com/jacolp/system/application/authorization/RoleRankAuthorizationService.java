package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.PermissionDeniedException;
import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Centralizes role-code identity checks and rank-based management ordering. */
@Service
public final class RoleRankAuthorizationService {

    private static final String CREATOR = "CREATOR";
    private static final String ADMIN = "ADMIN";

    private final RoleMetadataRepository roleMetadataRepository;

    public RoleRankAuthorizationService(RoleMetadataRepository roleMetadataRepository) {
        this.roleMetadataRepository = Objects.requireNonNull(roleMetadataRepository, "roleMetadataRepository");
    }

    /** Requires the fixed management-role identity; it does not infer management authority from a numeric id. */
    public void requireManagementRole(Long roleId) {
        String roleCode = requiredRole(roleId).roleCode();
        if (!CREATOR.equals(roleCode) && !ADMIN.equals(roleCode)) {
            throw new PermissionDeniedException("权限不足：仅创建者和管理员可以管理其他用户");
        }
    }

    /** Requires the fixed creator identity for creator-only business operations. */
    public void requireCreator(Long roleId) {
        if (!CREATOR.equals(requiredRole(roleId).roleCode())) {
            throw new PermissionDeniedException("权限不足：仅创建者可以修改用户名");
        }
    }

    /** Requires the actor rank to be strictly higher than the target rank (smaller numeric rank is higher). */
    public void requireStrictlySuperior(Long actorRoleId, Long targetRoleId) {
        RoleMetadata actor = requiredRole(actorRoleId);
        RoleMetadata target = requiredRole(targetRoleId);
        if (actor.rank() >= target.rank()) {
            throw new PermissionDeniedException("权限不足：只能修改权限低于自己的用户");
        }
    }

    private RoleMetadata requiredRole(Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw invalidMetadata();
        }
        RoleMetadata role = validatedCatalogue().get(roleId);
        if (role == null) throw invalidMetadata();
        return role;
    }

    private Map<Long, RoleMetadata> validatedCatalogue() {
        List<RoleMetadata> catalogue = roleMetadataRepository.findAll();
        if (catalogue == null || catalogue.isEmpty()) {
            throw invalidMetadata();
        }
        Map<Long, RoleMetadata> rolesById = new LinkedHashMap<>();
        Map<Integer, RoleMetadata> rolesByRank = new LinkedHashMap<>();
        for (RoleMetadata role : catalogue) {
            if (!isValid(role) || rolesById.putIfAbsent(role.id(), role) != null
                    || rolesByRank.putIfAbsent(role.rank(), role) != null) {
                throw invalidMetadata();
            }
        }
        return rolesById;
    }

    private static boolean isValid(RoleMetadata role) {
        return role != null && role.id() != null && role.id() > 0
                && role.rank() != null && role.rank() > 0
                && role.roleCode() != null && !role.roleCode().isBlank();
    }

    private static IllegalStateException invalidMetadata() {
        return new IllegalStateException("Role metadata is unavailable or invalid");
    }
}
