package com.jacolp.middleware.module.note.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.note.application.service.NoteContextServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.jacolp.note.infrastructure.persistence.dataobject.NoteContextDO;
import com.jacolp.note.infrastructure.persistence.mapper.NoteContextMapper;

class NoteContextServiceImplTest {

    @Test
    void adminGetSourceReturnsPersistedMarkdown() {
        NoteContextMapper mapper = mock(NoteContextMapper.class);
        NoteContextDO context = new NoteContextDO();
        context.setMarkdownContent("# Note");
        when(mapper.selectByNoteId(7L)).thenReturn(context);

        NoteContextServiceImpl service = service(mapper);

        assertEquals("# Note", service.adminGetSource(7L));
        verify(mapper).selectByNoteId(7L);
    }

    @Test
    void adminGetSourceRejectsMissingContext() {
        NoteContextMapper mapper = mock(NoteContextMapper.class);
        when(mapper.selectByNoteId(7L)).thenReturn(null);

        assertThrows(BaseException.class, () -> service(mapper).adminGetSource(7L));
    }

    @Test
    void deleteByNoteIdsSkipsEmptyAndUsesOneBatchDeleteForValues() {
        NoteContextMapper mapper = mock(NoteContextMapper.class);
        NoteContextServiceImpl service = service(mapper);

        service.deleteByNoteIds(List.of());
        verifyNoInteractions(mapper);

        service.deleteByNoteIds(List.of(7L, 8L));
        verify(mapper).deleteByNoteIds(List.of(7L, 8L));
    }

    private static NoteContextServiceImpl service(NoteContextMapper mapper) {
        NoteContextServiceImpl service = new NoteContextServiceImpl();
        ReflectionTestUtils.setField(service, "noteContextMapper", mapper);
        return service;
    }
}
