package com.jacolp.system.exception;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.core.exception.BaseException;

public class UserIsBanException extends BaseException {
    public UserIsBanException(String message) {
        super(message);
    }

    public UserIsBanException() {
        super(UserConstant.USER_IS_BANNED);
    }
}
