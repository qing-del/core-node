package com.jacolp.system.application.port.out;

/** Result of atomically recording a failed email-code verification attempt. */
public enum EmailLoginCodeFailureDecision {

    RECORDED,
    INVALIDATED,
    STALE
}
