package com.jacolp.middleware.module.note.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

import java.util.List;

import com.jacolp.module.note.biz.application.service.NoteConvertServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.jacolp.exception.BaseException;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteConvertedDO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteConvertMapper;

class NoteConvertServiceImplTest {

    @Test
    void deleteRejectsMissingConversion() {
        NoteConvertMapper mapper = mock(NoteConvertMapper.class);
        when(mapper.deleteByNoteId(7L)).thenReturn(0);

        assertThrows(BaseException.class, () -> service(mapper).delete(7L));
        verify(mapper).deleteByNoteId(7L);
    }

    @Test
    void deleteAllSkipsEmptyAndUsesOneBatchDeleteForValues() {
        NoteConvertMapper mapper = mock(NoteConvertMapper.class);
        NoteConvertServiceImpl service = service(mapper);

        service.deleteAllByNoteIds(List.of());
        verify(mapper, never()).deleteByNoteIds(anyList());

        service.deleteAllByNoteIds(List.of(7L, 8L));
        verify(mapper).deleteByNoteIds(List.of(7L, 8L));
    }

    @Test
    void publishedConversionMapsStoredMetadataAndHtml() {
        NoteConvertMapper mapper = mock(NoteConvertMapper.class);
        NoteConvertedDO converted = new NoteConvertedDO();
        converted.setTitle("readme.md");
        converted.setTagsJson("[\"java\",\"note\"]");
        converted.setCreateTimeStr("2026-07-20");
        converted.setTocHtml("<nav>toc</nav>");
        converted.setBodyHtml("<p>body</p>");
        when(mapper.selectPublishedByNoteId(7L)).thenReturn(converted);

        var result = service(mapper).getPublishedNoteConvert(7L);

        assertEquals("readme.md", result.getMeta().getTitle());
        assertEquals(List.of("java", "note"), result.getMeta().getTags());
        assertEquals("2026-07-20", result.getMeta().getCreateTime());
        assertEquals("<nav>toc</nav>", result.getTocHtml());
        assertEquals("<p>body</p>", result.getBodyHtml());
    }

    private static NoteConvertServiceImpl service(NoteConvertMapper mapper) {
        NoteConvertServiceImpl service = new NoteConvertServiceImpl();
        ReflectionTestUtils.setField(service, "noteConvertMapper", mapper);
        return service;
    }
}
