package com.jacolp.note.application.service;

import com.jacolp.common.core.result.PageResult;
import com.jacolp.note.application.dto.note.PublicNoteQueryDTO;
import com.jacolp.note.application.vo.note.PublicNoteDetailVO;

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
