package com.jacolp.system.application.port.out;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CoreAgentAuthorizationCodeStoreContractTest {

    @Test
    void portExposesOnlyApplicationModelsAndDocumentsAtomicLifecycleSemantics() {
        assertThat(CoreAgentAuthorizationCodeStore.class.getPackageName()).doesNotContain("infrastructure");
        for (Method method : CoreAgentAuthorizationCodeStore.class.getDeclaredMethods()) {
            assertThat(method.toGenericString()).doesNotContain("infrastructure", "redis", "dataobject", "DO");
        }
        assertThat(Arrays.stream(CoreAgentAuthorizationCodeStore.class.getDeclaredMethods())
                .map(Method::getName)).containsExactlyInAnyOrder("replaceCurrent", "findByCode", "consume", "invalidateCurrent");
    }
}
