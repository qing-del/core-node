package com.jacolp.system.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色信息表 sys_role 对应持久化对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleName;
    private String roleCode;
    private Integer rank;
    private Integer dailyApiLimit;
    private Long maxStorageBytes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
