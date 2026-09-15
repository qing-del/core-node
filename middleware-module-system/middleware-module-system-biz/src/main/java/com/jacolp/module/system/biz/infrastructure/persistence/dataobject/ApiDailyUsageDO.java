package com.jacolp.module.system.biz.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日 API 使用量表 biz_api_daily_usage 对应持久化对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiDailyUsageDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private LocalDate recordDate;
    private Integer usedCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
