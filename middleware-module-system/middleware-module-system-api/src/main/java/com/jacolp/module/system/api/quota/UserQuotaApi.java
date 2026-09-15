package com.jacolp.module.system.api.quota;

import java.time.LocalDate;

/**
 * Cross-module quota contract. Implementations own all consistency checks and
 * persistence; callers must not access user or daily-usage mappers directly.
 */
public interface UserQuotaApi {

    QuotaSnapshot getQuota(long userId, QuotaType quotaType, LocalDate quotaDate);

    ConsumeQuotaResult consume(ConsumeQuotaCommand command);

    void rollback(ConsumeQuotaCommand command);
}
