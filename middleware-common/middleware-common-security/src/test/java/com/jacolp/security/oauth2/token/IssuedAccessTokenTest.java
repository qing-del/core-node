package com.jacolp.security.oauth2.token;

import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IssuedAccessTokenTest {

    private static final Instant ISSUED = Instant.parse("2026-08-11T00:00:00Z");
    private static final String JTI = "AAECAwQFBgcICQoLDA0ODw";

    @Test
    void acceptsValidTokenAndRedactsItsEncodedValue() {
        IssuedAccessToken token = new IssuedAccessToken("header.payload.signature", JTI, ISSUED, ISSUED.plusSeconds(1));

        assertThat(token.toString()).contains("tokenValue=<redacted>").doesNotContain("header.payload.signature");
    }

    @Test
    void rejectsInvalidValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IssuedAccessToken(" ", JTI, ISSUED, ISSUED.plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new IssuedAccessToken("token", "bad", ISSUED, ISSUED.plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new IssuedAccessToken("token", JTI, null, ISSUED.plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new IssuedAccessToken("token", JTI, ISSUED, ISSUED));
    }
}
