package com.jacolp.module.note.biz.infrastructure.persistence.mapper;

import java.util.List;

import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteEachMappingDO;
import com.jacolp.module.note.biz.infrastructure.persistence.projection.MappingProjections;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface NoteEachMappingMapper {

    List<NoteEachMappingDO> selectBySourceNoteId(@Param("sourceNoteId") Long sourceNoteId);

    NoteEachMappingDO selectById(@Param("id") Long id);

    /**
     * 根据 id 查询笔记内联笔记映射行，并验证用户 id
     * @param mappingId 笔记内联笔记映射 id
     * @param userId 用户 id（传入null的话不开启校验）
     * @return 笔记内联笔记映射行 （不存在 / 没有所属权的时候返回 null）
     */
    NoteEachMappingDO selectByIdWithValidUserId(@Param("id") Long mappingId, Long userId);

    int bindNoteBySourceIdAndParseName(@Param("sourceNoteId") Long sourceNoteId,
                                       @Param("parsedNoteName") String parseName,
                                       @Param("targetNoteId") Long targetNoteId,
                                       @Param("status") Short status);

    int batchBindNoteByIds(@Param("mappings") List<NoteEachMappingDO> mappings);

    int unbindNoteById(@Param("id") Long id);

    int batchInsertMappings(@Param("mappings") List<NoteEachMappingDO> mappings);

    int softDeleteBySourceNoteId(@Param("sourceNoteId") Long sourceNoteId);

    int softDeleteBySourceNoteIds(@Param("sourceNoteIds") List<Long> sourceNoteIds);

    int hardDeleteBySourceNoteIds(@Param("sourceNoteIds") List<Long> sourceNoteIds);

    int deleteSoftDeletedRows();

    /**
     * 统计指定笔记的内联笔记映射数量
     * @param noteId 笔记 id
     * @param status 状态快照（传入null代表全查）
     * @return 笔记内联笔记映射行数量
     */
    int countByNoteIdAndStatus(@Param("noteId") Long noteId,
                               @Param("status") Short status);

    int updateBySourceNoteIds(@Param("sourceNoteIds") List<Long> sourceNoteIds,
                              @Param("status") Short status);

    /**
     * 统计指定笔记的未绑定内联笔记数量
     * @param noteId 笔记 id
     * @return 未绑定内联笔记映射行数量
     */
    int countByNoteIdAndTargetIdIsNull(@Param("noteId") Long noteId);

    /**
     * 查询引用了指定笔记的源笔记列表（反向引用）
     * @param targetNoteId 目标笔记 id（即被引用的笔记）
     * @param userId 当前用户 id；传 null 时跳过归属/公开过滤（管理端使用）
     * @return 反向引用列表（包含源笔记标题、状态、跨用户标记等）
     */
    List<MappingProjections.NoteBacklink> selectBacklinksByTargetNoteId(@Param("targetNoteId") Long targetNoteId,
                                                                        @Param("userId") Long userId);

    /**
     * 查询访客可见的公开笔记双链摘要。
     */
    List<MappingProjections.NoteEachSimple> selectPublicEachNotesBySourceNoteId(@Param("sourceNoteId") Long sourceNoteId);
}
