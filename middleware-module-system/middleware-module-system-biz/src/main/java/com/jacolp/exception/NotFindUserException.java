package com.jacolp.exception;

public class NotFindUserException extends BaseException {
    public NotFindUserException() {
        super("用户不存在");
    }

    public NotFindUserException(String message) {
        super(message);
    }
}
