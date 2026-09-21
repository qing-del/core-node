package com.jacolp.system.exception;

import com.jacolp.common.core.exception.AuthenticationException;

public class PasswordIncorrectException extends AuthenticationException {

    public static final String MESSAGE = "请检查账号和密码是否正确";

    public PasswordIncorrectException(String message) {
        super(message, "Internal login password authentication failed");
    }

    public PasswordIncorrectException(String message, String logMessage) {
        super(message, logMessage);
    }

    public PasswordIncorrectException(String message, Throwable cause) {
        super(message, "Internal login password authentication failed", cause);
    }

    public PasswordIncorrectException() {
        super(MESSAGE, "Internal login credentials are invalid");
    }
}
