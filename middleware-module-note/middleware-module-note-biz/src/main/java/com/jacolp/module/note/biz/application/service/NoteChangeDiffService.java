package com.jacolp.module.note.biz.application.service;

import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteChangeDiffDO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface NoteChangeDiffService {
    /**
     * 获取 笔记是否存在特定状态的修改记录
     * <p>- NoteConstant.NOTE_DIFF_STATUS_PENDING = 0 表示处于待确认</p>
     * <p>- NoteConstant.NOTE_DIFF_STATUS_CONFIRMED = 1 表示已确认</p>
     * <p>- NoteConstant.NOTE_DIFF_STATUS_CANCELED = 2 表示已取消</p>
     * @param noteId 笔记 ID
     * @param status 笔记状态
     * @return 笔记是否存在特定状态的修改记录
     */
    boolean countByNoteIdAndStatus(Long noteId, Integer status);


    /**
     * 插入 笔记修改diff记录
     * @param noteChangeDiffEntity 笔记修改记录
     */
    void insert(NoteChangeDiffDO noteChangeDiffEntity);

    /**
     * 删除 笔记修改diff记录
     * @param noteIds 笔记ID列表
     */
    void deleteByNoteIds(List<Long> noteIds);

    /**
     * 获取 笔记修改diff记录
     * @param noteId 笔记ID
     * @param noteDiffStatusPending 笔记状态
     * @throws com.jacolp.exception.BaseException 找不到 diff 记录数据行会报错
     */
    @Select("SELECT * FROM biz_note_change_diff WHERE note_id = #{noteId} AND status = #{noteDiffStatusPending}")
    NoteChangeDiffDO getByNoteIdAndStatus(Long noteId, Integer noteDiffStatusPending);

    /**
     * 更新 笔记修改diff记录
     * @throws com.jacolp.exception.BaseException 更新失败会抛出异常
     */
    void updateStatus(Long noteId, Integer status);
}
