package com.jacolp.system.application.dto.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRequest;

/** HTTP representation of an email-login-code issuance request. */
public record EmailLoginCodeHttpRequest(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("email") String email) {

    public EmailLoginCodeIssueRequest toDomain(String socketRemoteAddress) {
        return new EmailLoginCodeIssueRequest(clientId, email, socketRemoteAddress);
    }

    @Override
    public String toString() {
        return "EmailLoginCodeHttpRequest[clientId=" + clientId + ']';
    }
}
