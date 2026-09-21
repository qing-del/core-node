package com.jacolp.system.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Direct role-to-permission relation persisted in {@code sys_role_perm}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roleId;
    private Long permId;
    private LocalDateTime grantTime;
}
