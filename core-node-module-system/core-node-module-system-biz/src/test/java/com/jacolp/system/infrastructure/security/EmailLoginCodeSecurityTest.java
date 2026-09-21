package com.jacolp.system.infrastructure.security;

import com.jacolp.system.application.port.out.EmailLoginCodeGenerator;
import com.jacolp.system.application.port.out.EmailLoginCodeProtector;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailLoginCodeSecurityTest {
    @Test
    void generatorImplementsAnApplicationOnlyPort() {
        assertThat(EmailLoginCodeGenerator.class.getDeclaredMethods()[0].getGenericReturnType().getTypeName())
                .doesNotContain(".infrastructure.");
        assertThat(new SecureEmailLoginCodeGenerator()).isInstanceOf(EmailLoginCodeGenerator.class);
    }

    @Test
    void generatorProducesSixDigitsAndPreservesLeadingZeroAtBoundaries() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(0, 42, 999_999);
        SecureEmailLoginCodeGenerator generator = new SecureEmailLoginCodeGenerator(random);
        assertThat(generator.generate()).isEqualTo("000000");
        assertThat(generator.generate()).isEqualTo("000042");
        assertThat(generator.generate()).isEqualTo("999999");
        verify(random, org.mockito.Mockito.times(3)).nextInt(1_000_000);
    }

    @Test
    void bcryptProtectorProtectsAndVerifiesRealCodes() {
        BCryptEmailLoginCodeProtector protector = new BCryptEmailLoginCodeProtector(new PasswordEncoder());
        String verifier = protector.protect("000042");
        assertThat(protector.matches("000042", verifier)).isTrue();
        assertThat(protector.matches("000043", verifier)).isFalse();
        assertThat(protector.toString()).doesNotContain("000042", verifier);
    }

    @Test
    void nullVerifierStillUsesDummyBcryptComparison() {
        PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
        String dummy = new PasswordEncoder().encode("dummy");
        when(encoder.encode(anyString())).thenReturn(dummy);
        when(encoder.matches("000042", dummy)).thenReturn(false);
        EmailLoginCodeProtector protector = new BCryptEmailLoginCodeProtector(encoder);
        assertThat(protector.matches("000042", null)).isFalse();
        verify(encoder).matches("000042", dummy);
    }

    @Test
    void rejectsInvalidRawCodesAndBadVerifiersWithoutLeakingCode() {
        BCryptEmailLoginCodeProtector protector = new BCryptEmailLoginCodeProtector(new PasswordEncoder());
        assertThatIllegalArgumentException().isThrownBy(() -> protector.protect("12345"))
                .withMessage("Email login code must be exactly six decimal digits");
        assertThat(protector.matches(null, "bad")).isFalse();
        assertThat(protector.matches("12345", "bad")).isFalse();
        assertThat(protector.matches("12345a", "bad")).isFalse();
        assertThat(protector.matches("000042", "bad-verifier")).isFalse();
        assertThat(protector.toString()).doesNotContain("000042");
    }
}
