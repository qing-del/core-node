package com.jacolp.system.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Permission catalogue entry persisted in {@code sys_permission}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String oauthScope;
    private String resource;
    private String action;
    private String status;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
