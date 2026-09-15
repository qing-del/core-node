package com.jacolp.module.note.biz.infrastructure.persistence.mapper;

import java.util.List;

import com.jacolp.module.note.biz.application.vo.topic.TopicListVO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TopicDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.module.note.biz.infrastructure.persistence.dto.TopicNoteCountDTO;

@Mapper
/**
 * 主题数据访问层。
 */
public interface TopicMapper {

    /**
     * 按主题 ID + 用户 ID 查询，保证数据隔离。
     */
    @Select("SELECT id, user_id AS userId, topic_name AS topicName, parent_id AS parentId, sort_order AS sortOrder, create_time AS createTime, update_time AS updateTime FROM biz_topic WHERE id = #{id}")
    TopicDO selectById(@Param("id") Long id);

    /**
     * 查询同用户下是否存在同名主题。
     */
    @Select("SELECT id, user_id AS userId, topic_name AS topicName, parent_id AS parentId, sort_order AS sortOrder, create_time AS createTime, update_time AS updateTime FROM biz_topic WHERE user_id = #{userId} AND topic_name = #{topicName}")
    TopicDO selectByUserIdAndTopicName(@Param("userId") Long userId, @Param("topicName") String topicName);

    /**
     * 新增主题。
     */
    int insertTopic(TopicDO topic);

    /**
     * 修改主题。
     */
    int updateTopic(TopicDO topic);

    /**
     * 条件分页查询主题列表。
     */
    List<TopicListVO> listByCondition(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 删除前校验：查询目标主题及其未删除笔记数。
     */
    List<TopicNoteCountDTO> selectDeleteChecksByIds(@Param("ids") List<Long> ids);

    /**
     * 批量删除主题。
     */
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 按 ID 检查主题是否存在。
     */
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_topic WHERE id = #{topicId}")
    int countById(Long topicId);

    /**
     * 用户端条件查询：根据 scope 控制查询范围。
     * @param userId 用户 ID
     * @param keyword 关键词
     * @param globalScope 保留兼容参数，主题仅按用户隔离
     */
    List<TopicListVO> listByUserCondition(@Param("userId") Long userId,
                                          @Param("keyword") String keyword,
                                          @Param("globalScope") boolean globalScope);

    /**
     * 查询当前用户指定父级下的一层主题。
     */
    List<TopicListVO> listChildrenByParentId(@Param("userId") Long userId,
                                             @Param("parentId") Long parentId,
                                             @Param("rootOnly") boolean rootOnly);

    /**
     * 统计指定用户的主题数量。
     */
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_topic WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

}
