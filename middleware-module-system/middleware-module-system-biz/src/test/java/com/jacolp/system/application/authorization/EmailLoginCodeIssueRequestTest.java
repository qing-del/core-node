package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmailLoginCodeIssueRequestTest {

    @Test
    void acceptsUserOrAdminAndRedactsEmailAndSocketAddress() {
        EmailLoginCodeIssueRequest request = new EmailLoginCodeIssueRequest("user", "alice@example.test", "192.0.2.1");

        assertThat(request).isNotNull();
        assertThat(request.toString()).isEqualTo("EmailLoginCodeIssueRequest[clientId=user]");
    }

    @Test
    void rejectsInvalidClientEmailAndSocketAddress() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeIssueRequest("core_agent", "alice@example.test", "192.0.2.1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeIssueRequest("user", " alice@example.test", "192.0.2.1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeIssueRequest("user", "alice @example.test", "192.0.2.1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeIssueRequest("user", "alice@example.test\n", "192.0.2.1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new EmailLoginCodeIssueRequest("user", "alice@example.test", " "));
    }

    @Test
    void rejectionExceptionIsAnAuthenticationExceptionWithoutPii() {
        AuthenticationException exception = new EmailLoginCodeIssuanceRejectedException();

        assertThat(EmailLoginCodeIssuanceRejectedException.class).isAssignableTo(AuthenticationException.class);
        assertThat(exception).hasMessage("Email-code issuance rejected");
        assertThat(exception.getMessage()).doesNotContain("alice", "192.0.2.1");
    }
}
