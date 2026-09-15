package com.jacolp.module.system.biz.application.vo.user;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User overview information")
public class UserOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Login username")
    private String username;

    @Schema(description = "Nickname")
    private String nickname;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Role id: 1-creator, 2-admin, 3-user, 4-vip")
    private Long roleId;

    @Schema(description = "Status: 2-unactivated, 1-active, 0-disabled, -1-deleted")
    private Integer status;

    @Schema(description = "Maximum storage bytes")
    private Long maxStorageBytes;

    @Schema(description = "Used storage bytes")
    private Long usedStorageBytes;

    @Schema(description = "Register time")
    private LocalDateTime createTime;
}
