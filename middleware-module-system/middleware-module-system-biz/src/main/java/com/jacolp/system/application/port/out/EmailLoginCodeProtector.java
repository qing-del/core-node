package com.jacolp.system.application.port.out;

/** Protects and verifies one six-digit email login code without retaining the raw code. */
public interface EmailLoginCodeProtector {
    String protect(String rawCode);

    boolean matches(String rawCode, String verifier);
}
