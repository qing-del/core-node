package com.jacolp.audio.audio;

/** Pure lifecycle policy for persisted audio task status values. */
public final class AudioTaskLifecycle {

    public enum Status {
        PENDING(0),
        PROCESSING(1),
        SUCCESS(2),
        FAILED(-1),
        RETRIED(-2),
        CANCELLED(-3);

        private final int code;

        Status(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    private AudioTaskLifecycle() {
    }

    public static int initialStatus() {
        return Status.PENDING.code();
    }

    public static int callbackStartExpectedStatus() {
        return Status.PENDING.code();
    }

    public static int callbackStartResultStatus() {
        return Status.PROCESSING.code();
    }

    public static int callbackFinishExpectedStatus() {
        return Status.PROCESSING.code();
    }

    public static boolean isAllowedFinishStatus(int status) {
        return status == Status.SUCCESS.code() || status == Status.FAILED.code();
    }

    public static boolean shouldSetCompletedDate(int status) {
        return status == Status.SUCCESS.code();
    }

    public static int cancelledStatus() {
        return Status.CANCELLED.code();
    }

    public static boolean isCancellable(int status) {
        return status == Status.PENDING.code() || status == Status.PROCESSING.code();
    }

    public static boolean canTransition(int currentStatus, int targetStatus) {
        return (currentStatus == Status.PENDING.code() && targetStatus == Status.PROCESSING.code())
                || (currentStatus == Status.PROCESSING.code()
                && (targetStatus == Status.SUCCESS.code() || targetStatus == Status.FAILED.code()));
    }
}
