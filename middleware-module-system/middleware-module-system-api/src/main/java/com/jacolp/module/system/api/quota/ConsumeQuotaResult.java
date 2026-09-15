package com.jacolp.module.system.api.quota;

import java.util.Objects;

/**
 * Result of an atomic quota consumption attempt. A rejected request returns
 * {@code consumed=false} together with the observed quota state.
 */
public record ConsumeQuotaResult(boolean consumed, QuotaSnapshot quota) {

    public ConsumeQuotaResult {
        Objects.requireNonNull(quota, "quota must not be null");
    }
}
