package com.jacolp.middleware.module.audit.api;

import com.jacolp.module.audit.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditApplicationContractTest {

    @Test
    void commandsAndResultsCarryExplicitActorAndTargetInformation() {
        CreateAuditApplicationCommand create = new CreateAuditApplicationCommand(
                AuditTargetType.IMAGE, 11L, 22L, "please review", "image.png", "https://example.test/image.png");
        CancelAuditApplicationCommand cancel = new CancelAuditApplicationCommand(
                AuditTargetType.IMAGE, 11L, 22L);
        AuditApplicationResult created = new AuditApplicationResult(33L, AuditTargetType.IMAGE, 11L, 22L);
        CancelAuditApplicationResult cancelled = new CancelAuditApplicationResult(
                AuditTargetType.IMAGE, 11L, 22L, 1);

        assertEquals(22L, create.applicantUserId());
        assertEquals("image.png", create.targetName());
        assertEquals(22L, cancel.actorUserId());
        assertEquals(33L, created.auditApplicationId());
        assertEquals(1, cancelled.cancelledCount());
    }

    @Test
    void contractRejectsMissingOrInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new PendingAuditApplicationQuery(AuditTargetType.NOTE, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new CreateAuditApplicationCommand(AuditTargetType.TAG, 1L, null, null, "tag", null));
        assertThrows(IllegalArgumentException.class,
                () -> new CreateAuditApplicationCommand(AuditTargetType.TAG, 1L, 2L, null, " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> new CancelAuditApplicationResult(AuditTargetType.TAG, 1L, 2L, -1));
    }
}
