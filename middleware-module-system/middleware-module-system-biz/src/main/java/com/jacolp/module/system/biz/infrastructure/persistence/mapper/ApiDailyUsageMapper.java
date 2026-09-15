package com.jacolp.module.system.biz.infrastructure.persistence.mapper;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.ApiDailyUsageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface ApiDailyUsageMapper {

    @Select("SELECT * FROM biz_api_daily_usage WHERE user_id = #{userId} AND record_date = #{date}")
    ApiDailyUsageDO selectByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** 原子递增当日用量，不存在则插入。 */
    int incrementUsage(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** 原子递增指定的当日用量，不存在则插入。 */
    int incrementUsageBy(@Param("userId") Long userId,
                         @Param("date") LocalDate date,
                         @Param("amount") long amount);

    /** 原子递减当日用量。 */
    int decrementUsage(Long userId, LocalDate today);

    /** 原子递减指定的当日用量。 */
    int decrementUsageBy(@Param("userId") Long userId,
                         @Param("date") LocalDate date,
                         @Param("amount") long amount);
}
