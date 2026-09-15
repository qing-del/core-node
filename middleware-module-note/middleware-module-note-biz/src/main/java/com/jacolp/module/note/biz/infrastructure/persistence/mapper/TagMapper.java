package com.jacolp.module.note.biz.infrastructure.persistence.mapper;

import java.util.List;

import com.jacolp.module.note.biz.infrastructure.persistence.dto.TagNoteCountDTO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TagDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.module.note.biz.application.vo.tag.TagVO;

@Mapper
public interface TagMapper {

    int insertTag(TagDO tag);

    int batchInsertTags(@Param("tags") List<TagDO> tags);

    @Select("SELECT tag_name FROM biz_tag WHERE user_id = #{userId} AND audit_status != 4")
    List<String> selectTagNamesByUserId(@Param("userId") Long userId);

    @Select("SELECT id, user_id AS userId, tag_name AS tagName, audit_status AS auditStatus, create_time AS createTime FROM biz_tag WHERE user_id = #{userId} AND audit_status != 4")
    List<TagDO> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT id, user_id AS userId, tag_name AS tagName, audit_status AS auditStatus, create_time AS createTime FROM biz_tag WHERE user_id = #{userId} AND tag_name = #{tagName} AND audit_status != 4")
    TagDO selectByUserIdAndTagName(@Param("userId") Long userId, @Param("tagName") String tagName);

    @Select("SELECT id, user_id AS userId, tag_name AS tagName, audit_status AS auditStatus, create_time AS createTime FROM biz_tag WHERE id = #{id} AND user_id = #{userId} AND audit_status != 4")
    TagDO selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<TagDO> selectByIds(@Param("ids") List<Long> ids);

    int updateTag(TagDO tag);

    /**
     * 批量查询待删除标签
     * @param userId 对应用户的 id | 传入 null 时不做用户筛选
     * @param ids 标签 id 列表
     * @return 检查结果
     */
    List<TagNoteCountDTO> selectDeleteChecksByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    int deleteByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    int updateAuditStatusByIds(@Param("ids") List<Long> ids,
                               @Param("auditStatus") Short auditStatus);

    /**
     * 根据条件查询标签
     * @param userId
     * @param keyword
     * @return
     */
    List<TagVO> listByCondition(@Param("userId") Long userId, @Param("keyword") String keyword);

    List<TagDO> selectIdsByNamesAndUserId(List<String> tagNames, Long userId);

    /**
     * 用户端条件查询：根据 scope 控制查询范围。
     * @param userId 用户 ID
     * @param keyword 关键词
     * @param globalScope true=全局模式（自己的+别人已通过），false=仅自己
     */
    List<TagVO> listByUserCondition(@Param("userId") Long userId,
                                    @Param("keyword") String keyword,
                                    @Param("globalScope") boolean globalScope);

    /**
     * 根据标签id查询标签
     * @param tagId
     * @return
     */
    @Select("SELECT id, user_id AS userId, tag_name AS tagName, audit_status AS auditStatus, create_time AS createTime FROM biz_tag WHERE id = #{tagId} AND audit_status != 4")
    TagDO selectById(Long tagId);

    /**
     * 统计指定用户的标签数量。
     */
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_tag WHERE user_id = #{userId} AND audit_status != 4")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 统计指定用户已通过审核的标签数量。
     */
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_tag WHERE user_id = #{userId} AND audit_status = 2")
    long countPassedByUserId(@Param("userId") Long userId);
}
