package com.jacolp.note.infrastructure.persistence.mapper;

import com.jacolp.note.infrastructure.persistence.dataobject.NoteContextDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 笔记内容表 Mapper
 */
@Mapper
public interface NoteContextMapper {

    /**
     * 根据 note_id 查询笔记内容
     */
    NoteContextDO selectByNoteId(Long noteId);

    /**
     * 新增笔记内容
     */
    int insertContext(NoteContextDO entity);

    /**
     * 更新笔记内容（UPSERT 语义）
     */
    int updateContext(NoteContextDO entity);

    /**
     * 删除笔记内容
     */
    int deleteByNoteId(Long noteId);

    /**
     * 批量删除笔记内容
     */
    int deleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    /**
     * 根据 note_id 查询笔记内容
     * <p>- 带有管理员校验</p>
     * @param noteId 笔记 ID
     * @param userId 用户 ID（传入 null 则不进行所有权校验）
     * @return 笔记内容（不存在 / 没有所有权会返回 null）
     */
    NoteContextDO selectByNoteIdWithValidUserId(@Param("noteId") Long noteId, @Param("userId") Long userId);
}
