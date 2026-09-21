package com.jacolp.system.application.authorization;

import com.jacolp.system.application.port.out.CoreAgentAuthorizationCodeStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class AccountAuthorizationStateRevocationServiceTest {

    @Test
    void serviceRevokesOnlyTheExactCoreAgentPointerAndOrchestratesThroughTheSingleMethod() {
        CoreAgentAuthorizationCodeStore store = mock(CoreAgentAuthorizationCodeStore.class);
        CoreAgentAccountAuthorizationStateRevocationService service =
                new CoreAgentAccountAuthorizationStateRevocationService(store);

        service.revokeForSecurityFieldChange(7L);

        verify(store).invalidateCurrent(7L, "core_agent");
        verifyNoMoreInteractions(store);
    }

    @Test
    void invalidUserIdFailsBeforeCallingTheStore() {
        CoreAgentAuthorizationCodeStore store = mock(CoreAgentAuthorizationCodeStore.class);
        CoreAgentAccountAuthorizationStateRevocationService service =
                new CoreAgentAccountAuthorizationStateRevocationService(store);

        assertThatIllegalArgumentException().isThrownBy(() -> service.revokeCurrentCoreAgentAuthorizationCode(null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.revokeForSecurityFieldChange(0L));
        verifyNoMoreInteractions(store);
    }

    @Test
    void redisFailurePropagatesWithoutRetryOrTranslation() {
        CoreAgentAuthorizationCodeStore store = mock(CoreAgentAuthorizationCodeStore.class);
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        doThrow(failure).when(store).invalidateCurrent(7L, "core_agent");
        CoreAgentAccountAuthorizationStateRevocationService service =
                new CoreAgentAccountAuthorizationStateRevocationService(store);

        assertThatThrownBy(() -> service.revokeForSecurityFieldChange(7L)).isSameAs(failure);
        verify(store).invalidateCurrent(7L, "core_agent");
    }

}
