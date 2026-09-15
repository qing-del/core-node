package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.PermissionMetadata;
import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.PermissionMetadataRepository;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves direct and inherited role permissions from the rank-ordered catalogue.
 * A lower numeric rank is more privileged and therefore inherits every higher rank.
 */
@Service
@RequiredArgsConstructor
public class EffectiveRolePermissionResolver {

    private static final String ACTIVE_STATUS = "active";

    private static final Comparator<RoleMetadata> ROLE_ORDER = Comparator
            .comparing(RoleMetadata::rank)
            .thenComparing(RoleMetadata::id);

    private final RoleMetadataRepository roleMetadataRepository;
    private final PermissionMetadataRepository permissionMetadataRepository;

    public EffectiveRolePermissions resolve(Long currentRoleId) {
        if (currentRoleId == null) {
            throw invalid("Current role id is required");
        }

        RoleMetadata currentRole = requiredCurrentRole(currentRoleId);
        List<RoleMetadata> roleCatalogue = requiredRoleCatalogue();
        Map<Long, RoleMetadata> rolesById = validateRoleCatalogue(roleCatalogue);
        RoleMetadata catalogueCurrentRole = rolesById.get(currentRoleId);
        if (!currentRole.equals(catalogueCurrentRole)) {
            throw invalid("Current role metadata differs from the role catalogue");
        }

        List<Long> inheritedRoleIds = roleCatalogue.stream()
                .filter(role -> role.rank() >= currentRole.rank())
                .sorted(ROLE_ORDER)
                .map(RoleMetadata::id)
                .toList();
        List<PermissionMetadata> permissions = permissionMetadataRepository.findActiveByRoleIds(inheritedRoleIds);
        List<String> permissionCodes = validateAndSortPermissions(permissions);
        return new EffectiveRolePermissions(currentRole.id(), currentRole.roleCode(), currentRole.rank(), permissionCodes);
    }

    private RoleMetadata requiredCurrentRole(Long currentRoleId) {
        Optional<RoleMetadata> role = roleMetadataRepository.findById(currentRoleId);
        if (role == null || role.isEmpty() || !isValidRole(role.get()) || !currentRoleId.equals(role.get().id())) {
            throw invalid("Current role is missing or malformed");
        }
        return role.get();
    }

    private List<RoleMetadata> requiredRoleCatalogue() {
        List<RoleMetadata> roleCatalogue = roleMetadataRepository.findAll();
        if (roleCatalogue == null) {
            throw invalid("Role catalogue is missing");
        }
        return roleCatalogue;
    }

    private static Map<Long, RoleMetadata> validateRoleCatalogue(List<RoleMetadata> roleCatalogue) {
        Map<Long, RoleMetadata> rolesById = new LinkedHashMap<>();
        Map<Integer, RoleMetadata> rolesByRank = new LinkedHashMap<>();
        for (RoleMetadata role : roleCatalogue) {
            if (!isValidRole(role)) {
                throw invalid("Role catalogue contains malformed metadata");
            }
            if (rolesById.putIfAbsent(role.id(), role) != null || rolesByRank.putIfAbsent(role.rank(), role) != null) {
                throw invalid("Role catalogue contains duplicate id or rank");
            }
        }
        return rolesById;
    }

    private static List<String> validateAndSortPermissions(List<PermissionMetadata> permissions) {
        if (permissions == null) {
            throw invalid("Permission query returned no result");
        }
        Map<String, PermissionMetadata> permissionsByCode = new LinkedHashMap<>();
        for (PermissionMetadata permission : permissions) {
            if (!isValidActivePermission(permission)) {
                throw invalid("Permission query contains inactive or malformed metadata");
            }
            PermissionMetadata previous = permissionsByCode.putIfAbsent(permission.code(), permission);
            if (previous != null && !previous.equals(permission)) {
                throw invalid("Permission query contains conflicting metadata for one code");
            }
        }
        return permissionsByCode.keySet().stream().sorted().toList();
    }

    private static boolean isValidRole(RoleMetadata role) {
        return role != null
                && role.id() != null
                && role.rank() != null
                && role.rank() > 0
                && hasText(role.roleCode());
    }

    private static boolean isValidActivePermission(PermissionMetadata permission) {
        return permission != null
                && permission.id() != null
                && permission.id() > 0
                && ACTIVE_STATUS.equals(permission.status())
                && hasText(permission.code())
                && hasText(permission.resource())
                && hasText(permission.action());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }
}
