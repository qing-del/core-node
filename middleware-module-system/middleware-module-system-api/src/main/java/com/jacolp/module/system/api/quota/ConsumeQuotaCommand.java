package com.jacolp.module.system.api.quota;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A requested quota consumption. {@code quotaDate} is required for daily API
 * usage and must be absent for storage usage.
 */
public record ConsumeQuotaCommand(long userId, QuotaType quotaType, long amount, LocalDate quotaDate) {

    public ConsumeQuotaCommand {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(quotaType, "quotaType must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (quotaType == QuotaType.DAILY_API_CALL && quotaDate == null) {
            throw new IllegalArgumentException("quotaDate is required for daily API quota");
        }
        if (quotaType == QuotaType.STORAGE_BYTES && quotaDate != null) {
            throw new IllegalArgumentException("quotaDate is not applicable to storage quota");
        }
    }

    public static ConsumeQuotaCommand dailyApiCall(long userId, long amount, LocalDate quotaDate) {
        return new ConsumeQuotaCommand(userId, QuotaType.DAILY_API_CALL, amount, quotaDate);
    }

    public static ConsumeQuotaCommand storageBytes(long userId, long amount) {
        return new ConsumeQuotaCommand(userId, QuotaType.STORAGE_BYTES, amount, null);
    }
}
