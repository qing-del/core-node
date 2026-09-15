package com.jacolp.module.system.api.quota;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Current quota state returned by {@link UserQuotaApi}.
 */
public record QuotaSnapshot(long userId, QuotaType quotaType, long limit, long used, LocalDate quotaDate) {

    public QuotaSnapshot {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(quotaType, "quotaType must not be null");
        if (limit < 0 || used < 0) {
            throw new IllegalArgumentException("limit and used must not be negative");
        }
        if (quotaType == QuotaType.DAILY_API_CALL && quotaDate == null) {
            throw new IllegalArgumentException("quotaDate is required for daily API quota");
        }
        if (quotaType == QuotaType.STORAGE_BYTES && quotaDate != null) {
            throw new IllegalArgumentException("quotaDate is not applicable to storage quota");
        }
    }

    public long remaining() {
        return Math.max(0, limit - used);
    }
}
