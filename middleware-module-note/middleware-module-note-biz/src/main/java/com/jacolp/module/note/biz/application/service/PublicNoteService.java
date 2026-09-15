package com.jacolp.module.note.biz.application.service;

import com.jacolp.module.note.biz.application.dto.note.PublicNoteQueryDTO;
import com.jacolp.module.note.biz.application.vo.note.PublicNoteDetailVO;
import com.jacolp.result.PageResult;

public interface PublicNoteService {

    /**
     * 分页列出公开笔记。
     */
    PageResult listPublishedNotes(PublicNoteQueryDTO dto);

    /**
     * 获取公开笔记详情。
     */
    PublicNoteDetailVO getPublishedNoteDetail(Long noteId);
}
