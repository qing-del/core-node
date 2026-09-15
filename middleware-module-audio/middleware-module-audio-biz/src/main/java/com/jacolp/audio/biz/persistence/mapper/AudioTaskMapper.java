package com.jacolp.audio.biz.persistence.mapper;

import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AudioTaskMapper {
    int insert(AudioTaskDO task);
    AudioTaskDO selectById(Long id);
    List<AudioTaskDO> selectByUserId(@Param("dto") AudioTaskPageQueryDTO dto);
    /**
     * CAS 更新任务状态，WHERE status = expectedStatus 保证幂等性。
     * @return 影响行数，0 表示 CAS 失败（状态已变更）
     */
    int casUpdateStatus(@Param("id") Long id, @Param("expectedStatus") Integer expectedStatus, @Param("newStatus") Integer newStatus, @Param("resultUrl") String resultUrl, @Param("errorMsg") String errorMsg, @Param("completedDate") LocalDate completedDate);
    List<AudioTaskDO> selectPendingTimeout(@Param("before") LocalDateTime before);
    @Select("SELECT user_id FROM audio_task WHERE id = #{taskId}") Long getUserIdByTaskId(@NotNull(message = "taskId 不能为空") Long taskId);
}
