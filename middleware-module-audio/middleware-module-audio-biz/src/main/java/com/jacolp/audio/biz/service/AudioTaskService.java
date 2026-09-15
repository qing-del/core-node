package com.jacolp.audio.biz.service;

import com.jacolp.audio.biz.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.biz.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskSubmitDTO;
import com.jacolp.audio.biz.domain.vo.AudioTaskSubmitVO;
import com.jacolp.audio.biz.domain.vo.AudioTaskVO;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.result.PageResult;

public interface AudioTaskService {
    /** 提交音频任务。 */
    AudioTaskSubmitVO submitTask(AudioTaskSubmitDTO dto);
    /** 任务开始回调，会将任务状态更新为处理中。 */
    boolean callbackStart(AudioCallbackStartDTO dto);
    /** 任务结束回调，会更新任务最终状态和结果 URL。 */
    boolean callbackFinish(AudioCallbackFinishDTO dto);
    /** 获取任务详情。 */
    AudioTaskVO getTask(Long taskId);
    /** 获取任务列表；非管理员仅查询自己的任务。 */
    PageResult listTasks(AudioTaskPageQueryDTO dto);
    /** 重新加入任务队列。 */
    void requeueTask(AudioTaskDO task);
}
