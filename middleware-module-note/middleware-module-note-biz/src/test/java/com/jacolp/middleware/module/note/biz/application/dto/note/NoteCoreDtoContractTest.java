package com.jacolp.middleware.module.note.biz.application.dto.note;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jacolp.module.note.biz.application.dto.note.NoteQueryDTO;
import com.jacolp.module.note.biz.application.dto.note.UserNoteQueryDTO;
import com.jacolp.module.note.biz.application.dto.note.UserNoteSearchDTO;
import org.junit.jupiter.api.Test;

class NoteCoreDtoContractTest {

    @Test
    void migratedQueryDtosKeepLegacyPaginationDefaults() {
        assertEquals(1, new NoteQueryDTO().getPageNumOrDefault());
        assertEquals(15, new NoteQueryDTO().getPageSizeOrDefault());
        assertEquals(1, new UserNoteQueryDTO().getPageNumOrDefault());
        assertEquals(15, new UserNoteQueryDTO().getPageSizeOrDefault());
        assertEquals(1, new UserNoteSearchDTO().getPageNumOrDefault());
        assertEquals(15, new UserNoteSearchDTO().getPageSizeOrDefault());
    }
}
