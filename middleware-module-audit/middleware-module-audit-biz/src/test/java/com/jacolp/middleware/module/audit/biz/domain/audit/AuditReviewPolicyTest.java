package com.jacolp.middleware.module.audit.biz.domain.audit;

import com.jacolp.common.core.constant.AuditConstant;
import com.jacolp.common.core.enums.AuditStatus;
import com.jacolp.audit.api.AuditTargetType;
import com.jacolp.audit.domain.audit.AuditReviewPolicy;
import com.jacolp.audit.domain.audit.AuditReviewPolicy.Outcome;
import com.jacolp.audit.domain.audit.AuditReviewPolicy.ReviewMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditReviewPolicyTest {

    @Test
    void keepsTargetSpecificPendingAndResultCodesMutuallyExclusive() {
        assertTrue(AuditReviewPolicy.isPending(AuditTargetType.TAG, (short) 1));
        assertTrue(AuditReviewPolicy.isPending(AuditTargetType.IMAGE, (short) 1));
        assertTrue(AuditReviewPolicy.isPending(AuditTargetType.NOTE, (short) 0));
        assertFalse(AuditReviewPolicy.isPending(AuditTargetType.NOTE, (short) 1));
        assertFalse(AuditReviewPolicy.isPending(AuditTargetType.TAG, (short) 0));
        assertTrue(AuditReviewPolicy.isReviewResultAllowed(AuditTargetType.NOTE, (short) 1));
        assertTrue(AuditReviewPolicy.isReviewResultAllowed(AuditTargetType.NOTE, (short) 2));
        assertFalse(AuditReviewPolicy.isReviewResultAllowed(AuditTargetType.NOTE, (short) 3));
        assertTrue(AuditReviewPolicy.isReviewResultAllowed(AuditTargetType.TAG, (short) 2));
        assertTrue(AuditReviewPolicy.isReviewResultAllowed(AuditTargetType.IMAGE, (short) 3));
        assertFalse(AuditReviewPolicy.isReviewResultAllowed(AuditTargetType.TAG, (short) 1));
    }

    @Test
    void mapsOutcomeBackToEachLegacyResultCode() {
        assertEquals(Outcome.APPROVED, AuditReviewPolicy.outcome(AuditTargetType.NOTE, AuditConstant.PASS));
        assertEquals(Outcome.REJECTED, AuditReviewPolicy.outcome(AuditTargetType.NOTE, AuditConstant.REJECT));
        assertEquals(AuditStatus.APPROVED.getCode(),
                AuditReviewPolicy.resultStatus(AuditTargetType.IMAGE, Outcome.APPROVED));
        assertEquals(AuditStatus.REJECTED.getCode(),
                AuditReviewPolicy.resultStatus(AuditTargetType.TAG, Outcome.REJECTED));
    }

    @Test
    void mapsCancellationToEachLegacyRecordCode() {
        assertEquals(AuditConstant.CANCEL, AuditReviewPolicy.cancelledStatus(AuditTargetType.NOTE));
        assertEquals(AuditStatus.CANCELLED.getCode(), AuditReviewPolicy.cancelledStatus(AuditTargetType.TAG));
        assertEquals(AuditStatus.CANCELLED.getCode(), AuditReviewPolicy.cancelledStatus(AuditTargetType.IMAGE));
    }

    @Test
    void keepsGenericAuditStatusTransitionMatrix() {
        assertTrue(AuditStatus.WAIT.canTransitionTo(AuditStatus.AUDITING));
        assertTrue(AuditStatus.REJECTED.canTransitionTo(AuditStatus.AUDITING));
        assertTrue(AuditStatus.AUDITING.canTransitionTo(AuditStatus.APPROVED));
        assertTrue(AuditStatus.AUDITING.canTransitionTo(AuditStatus.REJECTED));
        assertFalse(AuditStatus.APPROVED.canTransitionTo(AuditStatus.AUDITING));
        assertFalse(AuditStatus.DELETED.canTransitionTo(AuditStatus.WAIT));
    }

    @Test
    void keepsBatchAndSingleImageRelationModesDistinct() {
        assertTrue(AuditReviewPolicy.shouldUpdateRelationStatus(ReviewMode.BATCH));
        assertFalse(AuditReviewPolicy.shouldUpdateRelationStatus(ReviewMode.SINGLE_IMAGE_COMPATIBILITY));
    }
}
