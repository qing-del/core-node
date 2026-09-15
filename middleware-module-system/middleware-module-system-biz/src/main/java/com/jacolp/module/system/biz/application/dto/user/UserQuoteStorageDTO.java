package com.jacolp.module.system.biz.application.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuoteStorageDTO {
    private Long id;
    private Long roleId;
    private Long maxStorageBytes;
    private Long usedStorageBytes;
}
