package com.jacolp.common.messaging.event;

/** One independently retryable email command. Never include credentials in its content. */
public record EmailSendRequestedEvent(String recipient, String subject, String htmlContent,
                                      String category, String businessKey) {
    public EmailSendRequestedEvent {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (htmlContent == null) throw new IllegalArgumentException("htmlContent must not be null");
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (businessKey == null || businessKey.isBlank()) {
            throw new IllegalArgumentException("businessKey must not be blank");
        }
    }
}
