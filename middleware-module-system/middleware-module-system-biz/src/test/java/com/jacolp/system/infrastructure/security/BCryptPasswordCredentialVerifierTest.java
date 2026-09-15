package com.jacolp.system.infrastructure.security;

import com.jacolp.system.application.port.out.PasswordCredentialVerifier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BCryptPasswordCredentialVerifierTest {

    @Test
    void portDoesNotExposeInfrastructureTypes() {
        for (Method method : PasswordCredentialVerifier.class.getDeclaredMethods()) {
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain(".infrastructure.");
            for (java.lang.reflect.Type parameterType : method.getGenericParameterTypes()) {
                assertThat(parameterType.getTypeName()).doesNotContain(".infrastructure.");
            }
        }
    }

    @Test
    void verifiesRealBcryptCredentialsForCorrectAndIncorrectRawPasswords() {
        PasswordEncoder passwordEncoder = new PasswordEncoder();
        PasswordCredentialVerifier verifier = new BCryptPasswordCredentialVerifier(passwordEncoder);
        String storedHash = passwordEncoder.encode("correct-password");

        assertThat(verifier.matches("correct-password", storedHash)).isTrue();
        assertThat(verifier.matches("incorrect-password", storedHash)).isFalse();
        assertThat(verifier.toString()).doesNotContain("correct-password");
    }

    @Test
    void absentStoredHashStillExecutesOneDummyBcryptComparisonThenRejects() {
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        String validDummyHash = new PasswordEncoder().encode("not-the-candidate-password");
        when(passwordEncoder.encode(anyString())).thenReturn(validDummyHash);
        when(passwordEncoder.matches("candidate-password", validDummyHash)).thenReturn(false);

        PasswordCredentialVerifier verifier = new BCryptPasswordCredentialVerifier(passwordEncoder);

        assertThat(verifier.matches("candidate-password", null)).isFalse();

        verify(passwordEncoder).matches("candidate-password", validDummyHash);
    }

    @Test
    void nullAndBlankRawPasswordsFailClosedWithoutCallingBcryptMatches() {
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        String validDummyHash = new PasswordEncoder().encode("not-the-candidate-password");
        when(passwordEncoder.encode(anyString())).thenReturn(validDummyHash);
        PasswordCredentialVerifier verifier = new BCryptPasswordCredentialVerifier(passwordEncoder);

        assertThat(verifier.matches(null, validDummyHash)).isFalse();
        assertThat(verifier.matches("   ", validDummyHash)).isFalse();

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void malformedStoredHashesAreRejectedWithoutLeakingTheRawPasswordInAnException() {
        PasswordCredentialVerifier verifier = new BCryptPasswordCredentialVerifier(new PasswordEncoder());

        assertThat(verifier.matches("candidate-password", "not-a-bcrypt-hash")).isFalse();
        assertThatCode(() -> verifier.matches("candidate-password", "not-a-bcrypt-hash"))
                .doesNotThrowAnyException();
    }

    @Test
    void illegalArgumentFromTheEncoderIsTreatedAsAnInvalidCredential() {
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        String validDummyHash = new PasswordEncoder().encode("not-the-candidate-password");
        when(passwordEncoder.encode(anyString())).thenReturn(validDummyHash);
        when(passwordEncoder.matches("candidate-password", "bad-format"))
                .thenThrow(new IllegalArgumentException("malformed BCrypt credential"));
        PasswordCredentialVerifier verifier = new BCryptPasswordCredentialVerifier(passwordEncoder);

        assertThat(verifier.matches("candidate-password", "bad-format")).isFalse();
    }
}
