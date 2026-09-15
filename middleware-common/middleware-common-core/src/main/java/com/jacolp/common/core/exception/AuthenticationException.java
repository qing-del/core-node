package com.jacolp.common.core.exception;

public class AuthenticationException extends BaseException {

    private final String logMessage;

    public AuthenticationException() {
        this(null, null, null);
    }

    public AuthenticationException(String message) {
        this(message, message, null);
    }

    /**
     * Creates an authentication exception with a user-facing message and a separate, English log message.
     * The two messages are intentionally independent so authentication diagnostics do not force English text
     * into the API response consumed by the frontend.
     */
    public AuthenticationException(String message, String logMessage) {
        this(message, logMessage, null);
    }

    public AuthenticationException(String message, String logMessage, Throwable cause) {
        super(message, cause);
        this.logMessage = logMessage;
    }

    public String getLogMessage() {
        return logMessage == null ? getMessage() : logMessage;
    }
}
