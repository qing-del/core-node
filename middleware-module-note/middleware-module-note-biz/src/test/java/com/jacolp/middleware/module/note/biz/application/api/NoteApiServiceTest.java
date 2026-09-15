package com.jacolp.middleware.module.note.biz.application.api;

import com.jacolp.note.application.api.NoteAuditApplyApiService;
import com.jacolp.note.application.api.NoteReadApiService;
import com.jacolp.note.infrastructure.persistence.mapper.NoteEachMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteImageMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteTagMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.TagMapper;
import com.jacolp.note.api.command.ApplyNoteAuditCommand;
import com.jacolp.note.api.command.ApplyMediaRelationAuditCommand;
import com.jacolp.note.api.model.AuditDecision;
import com.jacolp.note.api.model.NoteLifecycleStatus;
import com.jacolp.note.api.model.NoteMediaReferenceSummary;
import com.jacolp.note.api.model.NoteSummary;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteImageMappingDO;
import com.jacolp.note.infrastructure.persistence.projection.MappingProjections.ImageNoteCount;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteApiServiceTest {

    @Test
    void noteSummariesUseOneBatchQueryAndOmitDeletedRows() {
        NoteMapper noteMapper = mock(NoteMapper.class);
        NoteDO visible = note(2L, (short) 5);
        NoteDO deleted = note(1L, (short) 8);
        when(noteMapper.selectByIds(List.of(2L, 1L))).thenReturn(List.of(visible, deleted));

        Map<Long, NoteSummary> summaries = new NoteReadApiService(noteMapper, mock(TagMapper.class),
                mock(NoteImageMappingMapper.class)).findNoteSummariesByIds(List.of(2L, 1L, 2L));

        assertEquals(new NoteSummary(2L, 9L, 3L, "readme.md", NoteLifecycleStatus.APPROVED), summaries.get(2L));
        assertEquals(1, summaries.size());
        verify(noteMapper).selectByIds(List.of(2L, 1L));
    }

    @Test
    void mediaReferenceCountsInitializeUnreferencedIdsToZeroInOneBatchCall() {
        NoteImageMappingMapper mappingMapper = mock(NoteImageMappingMapper.class);
        ImageNoteCount count = new ImageNoteCount();
        count.setImageId(2L);
        count.setRefCount(4);
        when(mappingMapper.countByImageIds(List.of(2L, 1L))).thenReturn(List.of(count));

        Map<Long, Long> counts = new NoteReadApiService(mock(NoteMapper.class), mock(TagMapper.class), mappingMapper)
                .countMediaReferencesByMediaIds(List.of(2L, 1L, 2L));

        assertEquals(Map.of(2L, 4L, 1L, 0L), counts);
        verify(mappingMapper).countByImageIds(List.of(2L, 1L));
    }

    @Test
    void mediaNoteSummariesAndCompatibilityProjectionUseBatchQueries() {
        NoteMapper noteMapper = mock(NoteMapper.class);
        NoteImageMappingMapper mappingMapper = mock(NoteImageMappingMapper.class);
        NoteImageMappingDO mapping = new NoteImageMappingDO();
        mapping.setNoteId(7L);
        mapping.setImageId(2L);
        mapping.setNoteTitle("readme.md");
        mapping.setIsCrossUser((short) 1);
        mapping.setStatus((short) 6);
        mapping.setCreateTime(LocalDateTime.of(2026, 7, 20, 3, 0));
        when(mappingMapper.selectActiveByImageIds(List.of(2L, 1L))).thenReturn(List.of(mapping));
        when(noteMapper.selectByIds(List.of(7L))).thenReturn(List.of(note(7L, (short) 6)));

        NoteReadApiService adapter = new NoteReadApiService(
                noteMapper, mock(TagMapper.class), mappingMapper);
        Map<Long, List<NoteSummary>> summaries =
                adapter.findNoteSummariesByMediaIds(List.of(2L, 1L, 2L));
        Map<Long, List<NoteMediaReferenceSummary>> compatibility =
                adapter.findNoteMediaReferenceSummariesByMediaIds(List.of(2L, 1L, 2L));

        assertEquals(NoteLifecycleStatus.PUBLISHED, summaries.get(2L).getFirst().status());
        assertEquals(List.of(), summaries.get(1L));
        assertEquals(new NoteMediaReferenceSummary(7L, "readme.md", (short) 1, (short) 6,
                LocalDateTime.of(2026, 7, 20, 3, 0)), compatibility.get(2L).getFirst());
        assertEquals(List.of(), compatibility.get(1L));
        verify(mappingMapper, times(2)).selectActiveByImageIds(List.of(2L, 1L));
        verify(noteMapper).selectByIds(List.of(7L));
    }

    @Test
    void noteAuditMapsApprovalToLegacyNoteAndRelationStatuses() {
        NoteMapper noteMapper = mock(NoteMapper.class);
        NoteEachMappingMapper eachMapper = mock(NoteEachMappingMapper.class);
        when(noteMapper.updateStatusByIds(List.of(7L), (short) 5)).thenReturn(1);
        when(eachMapper.updateBySourceNoteIds(List.of(7L), (short) 1)).thenReturn(3);

        var result = new NoteAuditApplyApiService(noteMapper, mock(TagMapper.class), eachMapper,
                mock(NoteTagMappingMapper.class), mock(NoteImageMappingMapper.class)).applyNoteAudit(
                        new ApplyNoteAuditCommand(List.of(7L), AuditDecision.APPROVED));

        assertEquals(1, result.noteRowsUpdated());
        assertEquals(3, result.relationRowsUpdated());
        verify(noteMapper).updateStatusByIds(List.of(7L), (short) 5);
        verify(eachMapper).updateBySourceNoteIds(List.of(7L), (short) 1);
    }

    @Test
    void mediaRelationAuditUpdatesAllRowsInOneBatch() {
        NoteImageMappingMapper imageMapper = mock(NoteImageMappingMapper.class);
        when(imageMapper.updateByImageIds(List.of(7L, 8L), (short) 3)).thenReturn(4);

        var result = new NoteAuditApplyApiService(mock(NoteMapper.class), mock(TagMapper.class),
                mock(NoteEachMappingMapper.class), mock(NoteTagMappingMapper.class), imageMapper)
                .applyMediaRelationAudit(new ApplyMediaRelationAuditCommand(List.of(7L, 8L, 7L), AuditDecision.REJECTED));

        assertEquals(4, result.relationRowsUpdated());
        verify(imageMapper).updateByImageIds(List.of(7L, 8L), (short) 3);
    }

    private static NoteDO note(Long id, short status) {
        NoteDO note = new NoteDO();
        note.setId(id);
        note.setUserId(9L);
        note.setTopicId(3L);
        note.setTitle("readme.md");
        note.setStatus(status);
        return note;
    }
}
