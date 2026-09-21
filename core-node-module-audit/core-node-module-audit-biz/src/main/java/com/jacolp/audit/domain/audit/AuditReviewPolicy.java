package com.jacolp.audit.domain.audit;

import com.jacolp.common.core.constant.AuditConstant;
import com.jacolp.common.core.enums.AuditStatus;
import com.jacolp.audit.api.AuditTargetType;

import java.util.Objects;

/** Pure policy for the three legacy audit-record status schemes. */
public final class AuditReviewPolicy {

    public enum Outcome {
        APPROVED,
        REJECTED
    }

    public enum ReviewMode {
        BATCH,
        SINGLE_IMAGE_COMPATIBILITY
    }

    private AuditReviewPolicy() {
    }

    public static Short pendingStatus(AuditTargetType targetType) {
        return targetType == AuditTargetType.NOTE
                ? AuditConstant.WAIT
                : AuditStatus.AUDITING.getCode();
    }

    public static Short cancelledStatus(AuditTargetType targetType) {
        return targetType == AuditTargetType.NOTE ? AuditConstant.CANCEL : AuditStatus.CANCELLED.getCode();
    }

    public static boolean isPending(AuditTargetType targetType, Short status) {
        return Objects.equals(pendingStatus(targetType), status);
    }

    public static boolean isReviewResultAllowed(AuditTargetType targetType, Short status) {
        return targetType == AuditTargetType.NOTE
                ? AuditConstant.PASS.equals(status) || AuditConstant.REJECT.equals(status)
                : AuditStatus.APPROVED.getCode().equals(status) || AuditStatus.REJECTED.getCode().equals(status);
    }

    public static Outcome outcome(AuditTargetType targetType, Short status) {
        Short approvedStatus = resultStatus(targetType, Outcome.APPROVED);
        return approvedStatus.equals(status) ? Outcome.APPROVED : Outcome.REJECTED;
    }

    public static Short resultStatus(AuditTargetType targetType, Outcome outcome) {
        if (targetType == AuditTargetType.NOTE) {
            return outcome == Outcome.APPROVED ? AuditConstant.PASS : AuditConstant.REJECT;
        }
        return outcome == Outcome.APPROVED ? AuditStatus.APPROVED.getCode() : AuditStatus.REJECTED.getCode();
    }

    public static boolean shouldUpdateRelationStatus(ReviewMode reviewMode) {
        return reviewMode == ReviewMode.BATCH;
    }
}
