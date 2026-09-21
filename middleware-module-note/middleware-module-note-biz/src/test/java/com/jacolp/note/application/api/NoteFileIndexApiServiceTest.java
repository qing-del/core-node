package com.jacolp.note.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jacolp.note.api.model.NoteFileIndexSource;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NoteFileIndexApiServiceTest {

    @Test
    void mapsPublishedNoteToPublicFileIndexSource() {
        NoteMapper mapper = mock(NoteMapper.class);
        when(mapper.selectFileIndexPage(0L, 10)).thenReturn(List.of(note(7L, (short) 6)));

        List<NoteFileIndexSource> result = new NoteFileIndexApiService(mapper).listAfterId(0L, 10);

        assertThat(result).containsExactly(new NoteFileIndexSource(7L, 42L, "published", true, false));
    }

    @Test
    void keepsDeletedNoteInSourceReadModelForSoftDeleteProjection() {
        NoteMapper mapper = mock(NoteMapper.class);
        when(mapper.selectFileIndexById(7L)).thenReturn(note(7L, (short) 8));

        Optional<NoteFileIndexSource> result = new NoteFileIndexApiService(mapper).findById(7L);

        assertThat(result).contains(new NoteFileIndexSource(7L, 42L, "deleted", false, true));
    }

    private static NoteDO note(long id, short status) {
        return new NoteDO(id, 42L, null, status == 8 ? "deleted" : "published", null,
                1, status, (short) 0, 0, 0, 0L, null, null);
    }
}
