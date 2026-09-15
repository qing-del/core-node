package com.jacolp.audio.biz.persistence.mapper;

import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.biz.domain.vo.AudioTaskStatisticsVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AudioTaskMapper {
    int insert(AudioTaskDO task);
    AudioTaskDO selectById(Long id);
    List<AudioTaskDO> selectByUserId(@Param("dto") AudioTaskPageQueryDTO dto);
    AudioTaskStatisticsVO selectStatistics();

    int cancelTask(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("cancelledStatus") Integer cancelledStatus);
    int deleteTask(@Param("id") Long id, @Param("userId") Long userId);
    /**
     * CAS 更新任务状态，WHERE status = expectedStatus AND retry_time = attempt
     * 保证同一任务不同处理轮次之间互不覆盖。
     * @return 影响行数，0 表示 CAS 失败（状态已变更）
     */
    int casUpdateStatus(@Param("id") Long id, @Param("attempt") Integer attempt,
                        @Param("expectedStatus") Integer expectedStatus,
                        @Param("newStatus") Integer newStatus, @Param("resultUrl") String resultUrl,
                        @Param("audioSize") Long audioSize, @Param("errorMsg") String errorMsg,
                        @Param("completedDate") LocalDate completedDate);

    /**
     * 将指定用户的 FAILED 任务标记为 RETRIED，避免重复创建重试任务。
     *
     * @return 影响行数，0 表示任务不存在、无权限或状态已变更
     */
    int markTaskRetried(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("failedStatus") Integer failedStatus, @Param("retriedStatus") Integer retriedStatus);

    /**
     * 查询 PENDING 和 PROCESSING 状态的任务，且创建时间早于指定时间的任务
     * <p>最大限制返回 500 条数据</p>
     * @param before
     * @return
     */
    List<AudioTaskDO> selectPendingTimeout(@Param("before") LocalDateTime before);

    /**
     * 原子地为超时任务准备下一轮处理：重置为 PENDING 并将 retry_time +1。
     * @param id 音频任务主键 id
     * @param before 更新时间早于指定时间的任务才会被更新，避免重复更新
     * @return
     */
    int prepareRetry(@Param("id") Long id, @Param("before") LocalDateTime before);

    /**
     * 最大重试次数耗尽，将任务状态标记为 FAILED，并设置错误信息
     * @param id 音频任务主键 id
     * @param before 更新时间早于指定时间的任务才会被更新，避免重复更新
     * @param errorMsg 错误信息
     * @return
     */
    int markRetryExhausted(@Param("id") Long id, @Param("before") LocalDateTime before,
                           @Param("errorMsg") String errorMsg);
}
