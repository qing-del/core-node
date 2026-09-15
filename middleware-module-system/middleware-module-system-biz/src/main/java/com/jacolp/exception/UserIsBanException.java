package com.jacolp.exception;

import com.jacolp.constant.UserConstant;

public class UserIsBanException extends BaseException {
    public UserIsBanException(String message) {
        super(message);
    }

    public UserIsBanException() {
        super(UserConstant.USER_IS_BANNED);
    }
}
