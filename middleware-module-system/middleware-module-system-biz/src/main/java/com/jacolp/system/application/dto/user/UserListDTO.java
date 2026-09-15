package com.jacolp.system.application.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserListDTO {
    private Long id;
    private String username;
    private Integer status;
    private Long roleId;
    private Integer pageNum;
    private Integer pageSize;

    public int getPageNumOrDefault() {
        return pageNum == null ? 1 : pageNum;
    }

    public int getPageSizeOrDefault() {
        return pageSize == null ? 15 : pageSize;
    }
}
