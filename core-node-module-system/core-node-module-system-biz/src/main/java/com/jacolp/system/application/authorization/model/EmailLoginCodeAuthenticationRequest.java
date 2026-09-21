package com.jacolp.system.application.authorization.model;

import java.util.regex.Pattern;

/** Redacted input for authenticating one email-login code. */
public record EmailLoginCodeAuthenticationRequest(String email, String rawCode) {

    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    public EmailLoginCodeAuthenticationRequest {
        if (!isValidEmail(email) || rawCode == null || !CODE.matcher(rawCode).matches()) {
            throw new IllegalArgumentException("Invalid email-code authentication request");
        }
    }

    @Override
    public String toString() {
        return "EmailLoginCodeAuthenticationRequest[]";
    }

    private static boolean isValidEmail(String email) {
        return email != null && !email.isBlank() && email.length() <= 100
                && email.codePoints().noneMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint) || Character.isISOControl(codePoint));
    }
}
