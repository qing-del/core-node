package com.jacolp.middleware.module.note.api;

import com.jacolp.module.note.api.command.ApplyNoteAuditCommand;
import com.jacolp.module.note.api.model.AuditDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditContractTest {

    @Test
    void noteAuditCommandCopiesBatchIds() {
        List<Long> ids = new ArrayList<>(List.of(11L, 12L));
        ApplyNoteAuditCommand command = new ApplyNoteAuditCommand(ids, AuditDecision.APPROVED);

        ids.clear();

        assertEquals(List.of(11L, 12L), command.noteIds());
        assertThrows(UnsupportedOperationException.class, () -> command.noteIds().add(13L));
    }
}
