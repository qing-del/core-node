package com.jacolp.module.note.biz.application.service;

import java.util.List;

import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jacolp.constant.NoteConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteContextDO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteContextMapper;

/**
 * 笔记文本内容管理实现。
 *
 * <p>负责 {@code biz_note_context} 表的读写，存储笔记的 Markdown 原文。
 * 修改流程中通过 {@code markdown_content_new} 列暂存新版本，待确认后再覆盖。</p>
 */
@Service
public class NoteContextServiceImpl implements NoteContextService {

    @Autowired private NoteContextMapper noteContextMapper;

    /**
     * 获取笔记 Markdown 源内容（管理端，不做所有权校验）。
     * @param noteId 笔记 ID
     * @return Markdown 原文
     */
    @Override
    public String adminGetSource(Long noteId) {
        NoteContextDO context = noteContextMapper.selectByNoteId(noteId);
        if (context == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }
        return context.getMarkdownContent();
    }

    /**
     * 按笔记 ID 查询文本实体。
     * @param noteId 笔记 ID
     * @return 文本实体，不存在时返回 null
     */
    @Override
    public NoteContextDO getByNoteId(Long noteId) {
        return noteContextMapper.selectByNoteId(noteId);
    }

    /**
     * 获取笔记源文件
     * <p>- 自带通过 {@link com.jacolp.context.PermissionContext} 决定是否开启所属权校验</p>
     * @param noteId 笔记 ID
     * @return 笔记内容实体
     * @throws BaseException 笔记不存在 / 笔记无权限访问
     */
    @Override
    public NoteContextDO getByNoteIdWithValid(Long noteId) {
        NoteContextDO result;
        if (PermissionContext.isAdmin()) {
            result = noteContextMapper.selectByNoteIdWithValidUserId(noteId, null);
        } else {
            result = noteContextMapper.selectByNoteIdWithValidUserId(noteId, BaseContext.getCurrentId());
        }

        // 检查笔记是否存在 / 用户没有所属权也会返回 null
        if (result == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        NoteContextDO entity = new NoteContextDO();
        BeanUtils.copyProperties(result, entity);
        return entity;
    }

    /**
     * 新增笔记文本行。
     * @param noteContext 笔记文本实体
     */
    @Override
    public void insert(NoteContextDO noteContext) {
        noteContextMapper.insertContext(noteContext);
    }

    /**
     * 更新笔记文本（UPSERT 语义）。
     * @param noteContext 笔记文本实体
     */
    @Override
    public void update(NoteContextDO noteContext) {
        noteContextMapper.updateContext(noteContext);
    }

    /**
     * 批量删除笔记文本。
     * @param noteIds 笔记 ID 列表
     */
    @Override
    public void deleteByNoteIds(List<Long> noteIds) {
        if (noteIds != null && !noteIds.isEmpty()) {
            noteContextMapper.deleteByNoteIds(noteIds);
        }
    }
}
