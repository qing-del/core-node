package com.jacolp.common.core.exception;

/** A business authorization failure: scope, ownership, rank, or creator-only policy did not permit the operation. */
public class PermissionDeniedException extends BaseException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    public PermissionDeniedException() {
        super("无权限");
    }
}
