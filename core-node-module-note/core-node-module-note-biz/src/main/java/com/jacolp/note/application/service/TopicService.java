package com.jacolp.note.application.service;

import java.util.List;

import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.note.application.dto.topic.TopicAddDTO;
import com.jacolp.note.application.dto.topic.TopicListDTO;
import com.jacolp.note.application.dto.topic.TopicModifyDTO;
import com.jacolp.note.application.dto.topic.UserTopicQueryDTO;
import com.jacolp.note.application.vo.topic.TopicDetailVO;
import com.jacolp.note.application.vo.topic.TopicListVO;
import com.jacolp.note.application.vo.topic.TopicStatsVO;

public interface TopicService {

    void addTopic(TopicAddDTO dto);

    void modifyTopic(TopicModifyDTO dto);

    /**
     * 通过 ID 查询主题详情。
     * <p>- 通过使用 {@link PermissionContext#isAdmin()} 来判断是否需要校验所有权</p>
     */
    TopicDetailVO getTopicById(Long id);

    PageResult listTopics(TopicListDTO dto);

    /**
     * 批量删除主题。
     * <ol>
     *     <il>- 先全量校验主题是否存在、是否可删</il>
     *     <il>- 校验全部通过后再执行删除，减少回滚成本</il>
     * </ol>
     * <p>- 通过 {@link PermissionContext#isAdmin()} 来判断是否需要进行所有权校验</p>
     */
    void deleteTopics(List<Long> ids);

    /**
     * 用户端条件查询：仅查询当前用户自己的主题。
     */
    PageResult listUserTopics(UserTopicQueryDTO dto);

    /**
     * 查询当前用户指定父目录下的一层主题。
     */
    List<TopicListVO> listChildren(Long parentId);

    /**
     * 管理端查询指定用户指定父目录下的一层主题。
     */
    List<TopicListVO> listChildrenByUserId(Long userId, Long parentId);

    /**
     * 获取当前用户主题统计。
     */
    TopicStatsVO getUserTopicStats();

    /**
     * 校验主题是否存在
     * <p>- 存在且属于当前用户则为通过</p>
     * @param topicId
     * @return 通过 true，不通过 false
     */
    boolean topicValid(Long topicId);

}
