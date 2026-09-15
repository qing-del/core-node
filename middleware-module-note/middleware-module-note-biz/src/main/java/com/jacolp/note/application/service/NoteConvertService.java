package com.jacolp.note.application.service;

import java.util.List;

import com.jacolp.note.infrastructure.persistence.dataobject.NoteContextDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.application.vo.note.NoteConvertResultVO;

public interface NoteConvertService {
    String convertAndSave(NoteDO note, NoteContextDO context);

    void delete(Long noteId);

    void deleteAllByNoteIds(List<Long> noteIds);

    NoteConvertResultVO getNoteConvert(Long noteId);

    NoteConvertResultVO getPublishedNoteConvert(Long noteId);
}
