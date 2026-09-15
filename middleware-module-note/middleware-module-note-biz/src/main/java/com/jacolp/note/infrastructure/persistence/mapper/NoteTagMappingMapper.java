package com.jacolp.note.infrastructure.persistence.mapper;

import java.util.List;

import com.jacolp.note.infrastructure.persistence.dataobject.NoteTagMappingDO;
import com.jacolp.note.infrastructure.persistence.projection.MappingProjections;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NoteTagMappingMapper {

    List<NoteTagMappingDO> selectByNoteId(@Param("noteId") Long noteId);

    NoteTagMappingDO selectById(@Param("id") Long id);

    /**
     * 根据 id 查询笔记标签映射行，并验证用户 id
     * @param mappingId 笔记标签映射 id
     * @param userId 用户 id（传入null的话不开启校验）
     * @return 笔记标签映射行 （不存在 / 没有所属权的时候返回 null）
     */
    NoteTagMappingDO selectByIdWithValidUserId(@Param("id") Long mappingId, Long userId);

    List<Long> selectTagIdsByNoteId(@Param("noteId") Long noteId);

    int bindTagById(@Param("id") Long id,
                    @Param("tagId") Long tagId,
                    @Param("status") Short status);

    int batchBindTagByIds(@Param("mappings") List<NoteTagMappingDO> mappings);

    int unbindTagById(@Param("id") Long id);

    int batchInsertMappings(@Param("mappings") List<NoteTagMappingDO> mappings);

    int softDeleteByNoteId(@Param("noteId") Long noteId);

    int softDeleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    int hardDeleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    int softDeleteByNoteIdAndTagIds(@Param("noteId") Long noteId, @Param("tagIds") List<Long> tagIds);

    int deleteSoftDeletedRows();

    /**
     * 统计指定笔记的标签数量
     * @param noteId 笔记 id
     * @param status 状态快照（传入null代表全查）
     * @return 笔记标签映射行数量
     */
    int countByNoteIdAndStatus(@Param("noteId") Long noteId,
                               @Param("status") Short status);


    int updateByTagIds(@Param("tagIds") List<Long> tagIds, @Param("status") Short status);
    @Update("UPDATE biz_note_tag_mapping SET status = #{status}, update_time = NOW() " +
            "WHERE tag_id = #{tagId} AND is_deleted = 0")
    int updateActiveByTagId(@Param("tagId") Long tagId, @Param("status") Short status);

    /**
     * 统计指定笔记的未绑定标签数量
     * @param noteId 笔记 id
     * @return 未绑定标签映射行数量
     */
    int countByNoteIdAndTargetIdIsNull(@Param("noteId") Long noteId);

    /**
     * 统计指定标签的笔记数量
     * @param tagId 标签 id
     * @return 笔记标签映射行数量
     */
    @Select("SELECT COUNT(1) FROM biz_note_tag_mapping WHERE tag_id = #{tagId} AND is_deleted = 0")
    long countByTagId(Long tagId);

    /**
     * 统计指定笔记的未绑定标签数量
     * @param noteId 笔记 id
     * @return 未绑定标签映射行数量
     */
    @Select("SELECT COUNT(1) FROM biz_note_tag_mapping " +
            "WHERE note_id = #{noteId} AND tag_id IS NULL AND is_deleted = 0")
    int existNullTargetTag(Long noteId);

    /**
     * 查询引用了指定标签的源笔记列表（标签反向引用）
     * @param tagId 目标标签 id
     * @param userId 当前用户 id；传 null 时跳过归属/公开过滤（管理端使用）
     * @return 反向引用列表（包含源笔记标题、状态、跨用户标记等）
     */
    List<MappingProjections.TagBacklink> selectBacklinksByTagId(@Param("tagId") Long tagId,
                                                                @Param("userId") Long userId);

    /**
     * 查询访客可见的公开笔记标签名。
     */
    List<String> selectPublicTagNamesByNoteId(@Param("noteId") Long noteId);
}
