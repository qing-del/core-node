package com.jacolp.module.system.api.quota;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuotaContractTest {

    @Test
    void dailyApiQuotaRequiresAnExplicitDate() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumeQuotaCommand(1L, QuotaType.DAILY_API_CALL, 1L, null));

        ConsumeQuotaCommand command = ConsumeQuotaCommand.dailyApiCall(1L, 1L, LocalDate.of(2026, 7, 19));
        assertEquals(QuotaType.DAILY_API_CALL, command.quotaType());
    }

    @Test
    void storageQuotaDoesNotCarryDailyUsageState() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaSnapshot(1L, QuotaType.STORAGE_BYTES, 10L, 1L, LocalDate.now()));

        QuotaSnapshot snapshot = new QuotaSnapshot(1L, QuotaType.STORAGE_BYTES, 10L, 13L, null);
        assertEquals(0L, snapshot.remaining());
    }

    @Test
    void storageUpdateContextKeepsLegacyThreadLocalLifecycle() {
        Map<Long, Long> releasedBytes = Map.of(1L, 100L);
        StorageUpdateContext.setStorageMap(releasedBytes);

        assertEquals(releasedBytes, StorageUpdateContext.getStorageMap());

        StorageUpdateContext.clear();
        assertNull(StorageUpdateContext.getStorageMap());
    }
}
