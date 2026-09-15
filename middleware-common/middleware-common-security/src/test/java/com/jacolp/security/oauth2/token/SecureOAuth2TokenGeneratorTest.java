package com.jacolp.security.oauth2.token;

import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SecureOAuth2TokenGeneratorTest {

    @Test
    void generatesUrlSafeUnpaddedValuesWithRequiredEntropyLengths() {
        SecureOAuth2TokenGenerator generator = new SecureOAuth2TokenGenerator();

        String jti = generator.newJti();
        String opaqueToken = generator.newOpaqueToken();

        assertThat(jti).hasSize(22).matches("[A-Za-z0-9_-]+").doesNotContain("=");
        assertThat(opaqueToken).hasSize(43).matches("[A-Za-z0-9_-]+").doesNotContain("=");
        assertThat(Base64.getUrlDecoder().decode(jti)).hasSize(16);
        assertThat(Base64.getUrlDecoder().decode(opaqueToken)).hasSize(32);
    }

    @Test
    void generatesDistinctConsecutiveValues() {
        SecureOAuth2TokenGenerator generator = new SecureOAuth2TokenGenerator();

        assertThat(generator.newJti()).isNotEqualTo(generator.newJti());
        assertThat(generator.newOpaqueToken()).isNotEqualTo(generator.newOpaqueToken());
    }

    @Test
    void supportsDeterministicSecureRandomFixture() {
        SecureOAuth2TokenGenerator generator = new SecureOAuth2TokenGenerator(
                new DeterministicSecureRandom());

        assertThat(generator.newJti()).isEqualTo("AAECAwQFBgcICQoLDA0ODw");
        assertThat(generator.newOpaqueToken()).isEqualTo("EBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8");
    }

    private static final class DeterministicSecureRandom extends SecureRandom {

        private int nextByte;

        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) nextByte++;
            }
        }
    }
}
