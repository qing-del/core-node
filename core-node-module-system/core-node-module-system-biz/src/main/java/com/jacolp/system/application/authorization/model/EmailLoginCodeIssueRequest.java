package com.jacolp.system.application.authorization.model;

/** Validated non-secret input for issuing an email-login code. */
public record EmailLoginCodeIssueRequest(String clientId, String email, String socketRemoteAddress) {

    public EmailLoginCodeIssueRequest {
        if ((!"user".equals(clientId) && !"admin".equals(clientId))
                || !isValidEmail(email)
                || socketRemoteAddress == null || socketRemoteAddress.isBlank()) {
            throw new IllegalArgumentException("Invalid email-code issue request");
        }
    }

    @Override
    public String toString() {
        return "EmailLoginCodeIssueRequest[clientId=" + clientId + ']';
    }

    private static boolean isValidEmail(String email) {
        return email != null && !email.isBlank() && email.length() <= 100
                && email.codePoints().noneMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isISOControl(codePoint));
    }
}
