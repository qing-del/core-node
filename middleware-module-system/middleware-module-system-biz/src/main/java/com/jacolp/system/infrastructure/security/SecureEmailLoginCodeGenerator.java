package com.jacolp.system.infrastructure.security;

import com.jacolp.system.application.port.out.EmailLoginCodeGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;

/** Generates unbiased six-digit decimal email login codes. */
@Component
public final class SecureEmailLoginCodeGenerator implements EmailLoginCodeGenerator {
    private static final int CODE_BOUND = 1_000_000;
    private final SecureRandom secureRandom;

    public SecureEmailLoginCodeGenerator() {
        this(new SecureRandom());
    }

    SecureEmailLoginCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public String generate() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }
}
