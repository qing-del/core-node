package com.jacolp.middleware.module.note.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

import java.util.List;

import com.jacolp.module.note.biz.application.service.NoteChangeDiffServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.jacolp.exception.BaseException;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteChangeDiffDO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteChangeDiffMapper;

class NoteChangeDiffServiceImplTest {

    @Test
    void countDelegatesAndConvertsPositiveCountToTrue() {
        NoteChangeDiffMapper mapper = mock(NoteChangeDiffMapper.class);
        when(mapper.countByNoteIdAndStatus(7L, 0)).thenReturn(1);
        when(mapper.countByNoteIdAndStatus(8L, 0)).thenReturn(0);

        NoteChangeDiffServiceImpl service = service(mapper);

        assertTrue(service.countByNoteIdAndStatus(7L, 0));
        assertFalse(service.countByNoteIdAndStatus(8L, 0));
    }

    @Test
    void getByStatusRejectsMissingDiffAndReturnsStoredDiff() {
        NoteChangeDiffMapper mapper = mock(NoteChangeDiffMapper.class);
        when(mapper.selectByNoteIdAndStatus(7L, 0)).thenReturn(null);

        NoteChangeDiffServiceImpl service = service(mapper);
        assertThrows(BaseException.class, () -> service.getByNoteIdAndStatus(7L, 0));

        NoteChangeDiffDO diff = new NoteChangeDiffDO();
        when(mapper.selectByNoteIdAndStatus(8L, 0)).thenReturn(diff);
        assertSame(diff, service.getByNoteIdAndStatus(8L, 0));
    }

    @Test
    void updateStatusRejectsZeroAffectedRowsAndDeletesOnlyNonEmptyIds() {
        NoteChangeDiffMapper mapper = mock(NoteChangeDiffMapper.class);
        when(mapper.updateStatus(7L, 1)).thenReturn(0);
        NoteChangeDiffServiceImpl service = service(mapper);

        assertThrows(BaseException.class, () -> service.updateStatus(7L, 1));

        service.deleteByNoteIds(List.of());
        verify(mapper).updateStatus(7L, 1);
        verify(mapper, never()).deleteByNoteIds(anyList());

        service.deleteByNoteIds(List.of(7L, 8L));
        verify(mapper).deleteByNoteIds(List.of(7L, 8L));
    }

    private static NoteChangeDiffServiceImpl service(NoteChangeDiffMapper mapper) {
        NoteChangeDiffServiceImpl service = new NoteChangeDiffServiceImpl();
        ReflectionTestUtils.setField(service, "noteChangeDiffMapper", mapper);
        return service;
    }
}
