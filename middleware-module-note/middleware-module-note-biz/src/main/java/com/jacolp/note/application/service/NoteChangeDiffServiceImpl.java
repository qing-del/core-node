package com.jacolp.note.application.service;

import java.util.List;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteChangeDiffDO;
import com.jacolp.note.infrastructure.persistence.mapper.NoteChangeDiffMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 笔记变更差异（Diff）管理实现。
 *
 * <p>负责 {@code biz_note_change_diff} 表的读写。每次修改笔记源文件时，
 * Facade 会在此创建一条状态为"待确认"的 diff 记录；确认或取消后更新状态。</p>
 */
@Service
@Slf4j
public class NoteChangeDiffServiceImpl implements NoteChangeDiffService {

    @Autowired private NoteChangeDiffMapper noteChangeDiffMapper;

    /**
     * 检查笔记是否存在指定状态的修改记录。
     * @param noteId 笔记 ID
     * @param status 0=待确认, 1=已确认, 2=已取消
     * @return 存在返回 true
     */
    @Override
    public boolean countByNoteIdAndStatus(Long noteId, Integer status) {
        return noteChangeDiffMapper.countByNoteIdAndStatus(noteId, status) > 0;
    }

    /**
     * 插入或更新 diff 记录（UPSERT）。
     * @param noteChangeDiffEntity diff 实体
     */
    @Override
    public void insert(NoteChangeDiffDO noteChangeDiffEntity) {
        noteChangeDiffMapper.upsertDiff(noteChangeDiffEntity);
    }

    /**
     * 批量删除 diff 记录。
     * @param noteIds 笔记 ID 列表
     */
    @Override
    public void deleteByNoteIds(List<Long> noteIds) {
        if (noteIds != null && !noteIds.isEmpty()) {
            noteChangeDiffMapper.deleteByNoteIds(noteIds);
        }
    }

    @Override
    public NoteChangeDiffDO getByNoteIdAndStatus(Long noteId, Integer noteDiffStatusPending) {
        NoteChangeDiffDO diffEntity = noteChangeDiffMapper.selectByNoteIdAndStatus(noteId, noteDiffStatusPending);
        if (diffEntity == null) {
            throw new BaseException("变更记录不存在");
        }
        return diffEntity;
    }

    @Override
    public void updateStatus(Long noteId, Integer status) {
        int affected = noteChangeDiffMapper.updateStatus(noteId, status);
        if (affected == 0) {
            log.error("Failed to update note diff status! id = {}, status = {}", noteId, status);
            throw new BaseException("变更失败");
        }
    }
}
