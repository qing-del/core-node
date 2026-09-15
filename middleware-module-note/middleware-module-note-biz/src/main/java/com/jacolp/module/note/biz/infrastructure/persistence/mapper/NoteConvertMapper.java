package com.jacolp.module.note.biz.infrastructure.persistence.mapper;

import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteConvertedDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteConvertMapper {

    NoteConvertedDO selectByNoteId(@Param("noteId") Long noteId);

    int countByNoteId(@Param("noteId") Long noteId);

    /**
     * 插入或更新转换结果
     * <p>- 存在重复的 userId 是，会因为唯一性而进行更新和不是插入</p>
     * @param entity 转换内容实体类
     * @return 插入或更新数量
     */
    int upsertConverted(NoteConvertedDO entity);

    int deleteByNoteId(@Param("noteId") Long noteId);

    int deleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    /**
     * 根据 noteId 查询转换结果
     * <p>- 会返回笔记所属用户 ID</p>
     * @param noteId 笔记 ID
     * @param userId 笔记归属的用户 ID（传入 null 表示不校验）
     * @return 如果不存在 / 笔记不属于用户 -> null
     */
    NoteConvertedDO selectByNoteIdWithValidUserId(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /**
     * 查询已公开笔记的转换结果。
     */
    NoteConvertedDO selectPublishedByNoteId(@Param("noteId") Long noteId);
}
