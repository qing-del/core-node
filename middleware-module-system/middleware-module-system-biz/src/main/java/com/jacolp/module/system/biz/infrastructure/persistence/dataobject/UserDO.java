package com.jacolp.module.system.biz.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息表 sys_user 对应持久化对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private Long roleId;
    private Integer status;
    private Long maxStorageBytes;
    private Long usedStorageBytes;
    private Long noteCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
