package com.jacolp.system.exception;

import com.jacolp.common.core.exception.BaseException;

public class NotFindUserException extends BaseException {
    public NotFindUserException() {
        super("用户不存在");
    }

    public NotFindUserException(String message) {
        super(message);
    }
}
