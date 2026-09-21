package com.jacolp.system.domain.quota;

import com.jacolp.system.api.quota.QuotaType;

/** Pure arithmetic and query-shape rules for the existing user quota flows. */
public final class UserQuotaPolicy {

    public record StorageDecision(long newUsedBytes, boolean exceedsLimit, boolean underflows) {
    }

    private UserQuotaPolicy() {
    }

    public static boolean canConsume(long amount, long remaining) {
        return amount <= remaining;
    }

    public static long usedOrZero(Number used) {
        return used == null ? 0L : used.longValue();
    }

    public static boolean requiresQuotaDate(QuotaType quotaType) {
        return quotaType == QuotaType.DAILY_API_CALL;
    }

    public static StorageDecision storageDecision(long actualUsedBytes, long deltaBytes, long maxBytes) {
        long newUsedBytes = actualUsedBytes + deltaBytes;
        return new StorageDecision(newUsedBytes, newUsedBytes > maxBytes, newUsedBytes < 0);
    }

    public static boolean isNoStorageDelta(long deltaBytes) {
        return deltaBytes == 0;
    }
}
