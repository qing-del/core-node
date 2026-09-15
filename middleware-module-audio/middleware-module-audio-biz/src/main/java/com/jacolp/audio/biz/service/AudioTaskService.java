package com.jacolp.audio.biz.service;

import com.jacolp.audio.biz.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.biz.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskSubmitDTO;
import com.jacolp.audio.biz.domain.vo.AudioTaskSubmitVO;
import com.jacolp.audio.biz.domain.vo.AudioTaskVO;
import com.jacolp.audio.biz.domain.vo.AudioTaskStatisticsVO;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.result.PageResult;

public interface AudioTaskService {
    /**
     * 提交音频任务。
     */
    AudioTaskSubmitVO submitTask(AudioTaskSubmitDTO dto);

    /**
     * 任务开始回调，会将任务状态更新为处理中。
     */
    boolean callbackStart(AudioCallbackStartDTO dto);

    /**
     * 任务结束回调，会更新任务最终状态和结果 URL。
     */
    boolean callbackFinish(AudioCallbackFinishDTO dto);

    /**
     * 获取任务详情。
     */
    AudioTaskVO getTask(Long taskId);

    /**
     * 获取任务列表；非管理员仅查询自己的任务。
     */
    PageResult listTasks(AudioTaskPageQueryDTO dto);

    /**
     * 获取管理端音频任务统计：今日成功、今日失败、当前等待和当前处理中。
     */
    AudioTaskStatisticsVO getStatistics();

    /**
     * 取消排队中或处理中的任务；普通用户仅能取消自己的任务，管理员可取消任意任务。
     */
    boolean cancelTask(Long taskId);

    /**
     * 删除任务，并在事务提交后通知 Python 清理对应资源。
     */
    boolean deleteTask(Long taskId);

    /**
     * 复制当前用户的失败任务并重新投递，原任务将标记为已重试。
     */
    AudioTaskSubmitVO retryFailedTask(Long taskId);

    /**
     * 重新加入任务队列。
     */
    void requeueTask(AudioTaskDO task);
}
