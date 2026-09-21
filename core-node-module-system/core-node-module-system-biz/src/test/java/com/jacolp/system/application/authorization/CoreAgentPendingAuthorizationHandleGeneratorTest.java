package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreAgentPendingAuthorizationHandleGeneratorTest {

    private static final Instant EXPIRY = Instant.parse("2026-08-11T08:10:00Z");

    @Test
    void wrapsTheExistingSecureRandomOpaqueGeneratorInAValidatedRedactedHandle() {
        SecureOAuth2TokenGenerator generator = mock(SecureOAuth2TokenGenerator.class);
        String value = opaque((byte) 7);
        when(generator.newOpaqueToken()).thenReturn(value);

        IssuedCoreAgentAuthorizationPendingHandle handle = new CoreAgentPendingAuthorizationHandleGenerator(generator)
                .generate(EXPIRY);

        assertThat(handle.rawHandle()).isEqualTo(value);
        assertThat(handle.expiresAt()).isEqualTo(EXPIRY);
        assertThat(handle.toString()).doesNotContain(value);
    }

    @Test
    void rejectsMissingExpiryAndFailsClosedForBadOrNullGeneratorOutput() {
        SecureOAuth2TokenGenerator generator = mock(SecureOAuth2TokenGenerator.class);
        CoreAgentPendingAuthorizationHandleGenerator subject = new CoreAgentPendingAuthorizationHandleGenerator(generator);
        assertThatIllegalArgumentException().isThrownBy(() -> subject.generate(null));
        when(generator.newOpaqueToken()).thenReturn(null);
        assertThatIllegalStateException().isThrownBy(() -> subject.generate(EXPIRY));
        when(generator.newOpaqueToken()).thenReturn("not-opaque");
        assertThatIllegalArgumentException().isThrownBy(() -> subject.generate(EXPIRY));
    }

    private static String opaque(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
