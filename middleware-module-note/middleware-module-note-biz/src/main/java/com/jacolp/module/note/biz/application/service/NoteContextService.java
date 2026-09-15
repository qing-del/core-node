package com.jacolp.module.note.biz.application.service;

import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteContextDO;

import java.util.List;

public interface NoteContextService {

    /**
     * 获取笔记源文件 -- (管理员专属)
     * @param noteId 笔记 ID
     * @return
     */
    String adminGetSource(Long noteId);

    /**
     * 获取笔记源文件
     * @param noteId 笔记 ID
     * @return
     */
    NoteContextDO getByNoteId(Long noteId);

    /**
     * 获取笔记源文件
     * <p>- 自带通过 {@link com.jacolp.context.PermissionContext} 决定是否开启所属权校验</p>
     * @param noteId 笔记 ID
     * @return 笔记内容实体
     * @throws com.jacolp.exception.BaseException 笔记不存在 / 笔记无权限访问
     */
    NoteContextDO getByNoteIdWithValid(Long noteId);

    /**
     * 保存笔记
     * @param noteContext 笔记文本实体
     */
    void insert(NoteContextDO noteContext);

    /**
     * 更新笔记
     * @param noteContext 笔记文本实体
     */
    void update(NoteContextDO noteContext);

    /**
     * 批量删除笔记内容
     * @param noteIds 笔记 ID
     */
    void deleteByNoteIds(List<Long> noteIds);
}
