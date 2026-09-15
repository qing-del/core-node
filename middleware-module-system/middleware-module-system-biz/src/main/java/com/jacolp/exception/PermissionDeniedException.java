package com.jacolp.exception;

import com.jacolp.constant.UserConstant;

public class PermissionDeniedException extends BaseException {
    public PermissionDeniedException(String message) {
        super(message);
    }
    public PermissionDeniedException() {
        super(UserConstant.PERMISSION_DENIED);
    }
}
