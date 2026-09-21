package com.jacolp.system.application.port.out;

/** Generates one raw email login code for immediate protected storage. */
public interface EmailLoginCodeGenerator {
    String generate();
}
