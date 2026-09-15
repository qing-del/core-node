package com.jacolp.exception;

import com.jacolp.constant.UserConstant;

public class PasswordIncorrectException extends BaseException {
    public PasswordIncorrectException(String message) {
        super(message);
    }
    public PasswordIncorrectException(String message, Throwable cause) {
        super(message, cause);
    }
    public PasswordIncorrectException() {
        super(UserConstant.USER_PASSWORD_ERROR);
    }
}
