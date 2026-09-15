package com.jacolp.middleware.module.system.biz.domain.quota;

import com.jacolp.module.system.api.quota.QuotaType;
import com.jacolp.module.system.biz.domain.quota.UserQuotaPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserQuotaPolicyTest {

    @Test
    void preservesConsumptionBoundaryAndUsedValueNormalization() {
        assertTrue(UserQuotaPolicy.canConsume(10L, 10L));
        assertFalse(UserQuotaPolicy.canConsume(11L, 10L));
        assertTrue(UserQuotaPolicy.canConsume(-1L, 0L));
        assertEquals(0L, UserQuotaPolicy.usedOrZero(null));
        assertEquals(7L, UserQuotaPolicy.usedOrZero(7L));
    }

    @Test
    void preservesDailyDateRequirement() {
        assertTrue(UserQuotaPolicy.requiresQuotaDate(QuotaType.DAILY_API_CALL));
        assertFalse(UserQuotaPolicy.requiresQuotaDate(QuotaType.STORAGE_BYTES));
    }

    @Test
    void preservesStorageArithmeticAndBoundaryChecks() {
        UserQuotaPolicy.StorageDecision exactLimit = UserQuotaPolicy.storageDecision(90L, 10L, 100L);
        UserQuotaPolicy.StorageDecision exceedsLimit = UserQuotaPolicy.storageDecision(90L, 11L, 100L);
        UserQuotaPolicy.StorageDecision underflow = UserQuotaPolicy.storageDecision(5L, -6L, 100L);

        assertEquals(100L, exactLimit.newUsedBytes());
        assertFalse(exactLimit.exceedsLimit());
        assertTrue(exceedsLimit.exceedsLimit());
        assertTrue(underflow.underflows());
        assertTrue(UserQuotaPolicy.isNoStorageDelta(0L));
        assertFalse(UserQuotaPolicy.isNoStorageDelta(-1L));
    }
}
