package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAccountEligibilityServiceTest {

    @Test
    void userPasswordWithUserRoleProducesCredentialFreeIdentity() {
        Fixture fixture = fixture();
        AuthorizationAccount account = account(7L, 3L, UserConstant.ACTIVE_STATUS, "");
        when(fixture.roles.findById(3L)).thenReturn(Optional.of(role(3L, "USER", 3)));

        InternalAuthenticatedAccount actual = fixture.service.resolve(policy("user", "password"), account);

        assertThat(actual).isEqualTo(new InternalAuthenticatedAccount(7L, "alice", "alice@example.test", 3L, "USER", 3));
        assertThat(actual.toString()).doesNotContain("stored-password-hash");
        verify(fixture.roles).findById(3L);
    }

    @Test
    void adminClientAcceptsAdminAndCreatorRoles() {
        Fixture fixture = fixture();
        AuthorizationAccount admin = account(8L, 2L, UserConstant.ACTIVE_STATUS, "");
        AuthorizationAccount creator = account(9L, 1L, UserConstant.ACTIVE_STATUS, "");
        when(fixture.roles.findById(2L)).thenReturn(Optional.of(role(2L, "ADMIN", 2)));
        when(fixture.roles.findById(1L)).thenReturn(Optional.of(role(1L, "CREATOR", 1)));

        Assertions.assertThat(fixture.service.resolve(policy("admin", "email-code"), admin).roleCode()).isEqualTo("ADMIN");
        Assertions.assertThat(fixture.service.resolve(policy("admin", "password"), creator).roleCode()).isEqualTo("CREATOR");
    }

    @Test
    void managementRolesCanUseEitherInternalClientWhileUserRemainsClientBound() {
        Fixture fixture = fixture();
        when(fixture.roles.findById(2L)).thenReturn(Optional.of(role(2L, "ADMIN", 2)));
        when(fixture.roles.findById(1L)).thenReturn(Optional.of(role(1L, "CREATOR", 1)));
        when(fixture.roles.findById(3L)).thenReturn(Optional.of(role(3L, "USER", 3)));

        assertThat(fixture.service.resolve(policy("user", "password"), account(7L, 2L, 1, "")).roleCode())
                .isEqualTo("ADMIN");
        assertThat(fixture.service.resolve(policy("user", "password"), account(7L, 1L, 1, "")).roleCode())
                .isEqualTo("CREATOR");
        assertRejected(() -> fixture.service.resolve(policy("admin", "password"), account(7L, 3L, 1, "")),
                InternalAccountAuthenticationRejectedException.Reason.ROLE_NOT_ALLOWED);
    }

    @Test
    void inactiveAccountAndDeniedGrantUseTheSameAuthenticationRejection() {
        Fixture inactiveFixture = fixture();
        assertRejected(() -> inactiveFixture.service.resolve(policy("user", "password"),
                        account(7L, 3L, UserConstant.UNACTIVE_STATUS, "")),
                InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_NOT_ACTIVATED);
        assertRejected(() -> inactiveFixture.service.resolve(policy("user", "password"), account(7L, 3L, 0, "")),
                InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_DISABLED);

        AccountGrantTypeResolver grants = mock(AccountGrantTypeResolver.class);
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        when(grants.allows("password", "")).thenReturn(false);
        InternalAccountEligibilityService deniedService = new InternalAccountEligibilityService(grants, roles);
        assertRejected(() -> deniedService.resolve(policy("user", "password"), account(7L, 3L, 1, "")),
                InternalAccountAuthenticationRejectedException.Reason.GRANT_TYPE_NOT_ALLOWED);
    }

    @Test
    void missingOrCorruptRoleDirectoryMetadataFailsClosedAsConfigurationError() {
        Fixture missing = fixture();
        when(missing.roles.findById(3L)).thenReturn(Optional.empty());
        assertThatIllegalStateException().isThrownBy(() -> missing.service.resolve(policy("user", "password"),
                account(7L, 3L, 1, "")));

        Fixture mismatch = fixture();
        when(mismatch.roles.findById(3L)).thenReturn(Optional.of(role(2L, "USER", 3)));
        assertThatIllegalStateException().isThrownBy(() -> mismatch.service.resolve(policy("user", "password"),
                account(7L, 3L, 1, "")));

        Fixture invalidRank = fixture();
        when(invalidRank.roles.findById(3L)).thenReturn(Optional.of(role(3L, "USER", 0)));
        assertThatIllegalStateException().isThrownBy(() -> invalidRank.service.resolve(policy("user", "password"),
                account(7L, 3L, 1, "")));

        Fixture invalidCode = fixture();
        when(invalidCode.roles.findById(3L)).thenReturn(Optional.of(role(3L, " ", 3)));
        assertThatIllegalStateException().isThrownBy(() -> invalidCode.service.resolve(policy("user", "password"),
                account(7L, 3L, 1, "")));
    }

    @Test
    void malformedExtraGrantMetadataIsAConfigurationErrorNotAnAuthenticationRejection() {
        Fixture fixture = fixture();

        assertThatIllegalStateException().isThrownBy(() -> fixture.service.resolve(policy("user", "password"),
                account(7L, 3L, 1, "password")));
    }

    @Test
    void invalidInternalPolicyFailsClosedAsConfigurationError() {
        Fixture fixture = fixture();

        assertThatIllegalStateException().isThrownBy(() -> fixture.service.resolve(policy("core_agent", "password"),
                account(7L, 3L, 1, "")));
        assertThatIllegalStateException().isThrownBy(() -> fixture.service.resolve(policy("user", "refresh_token"),
                account(7L, 3L, 1, "")));
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                                       InternalAccountAuthenticationRejectedException.Reason reason) {
        assertThatThrownBy(callable)
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .isInstanceOf(AuthenticationException.class)
                .hasMessage(reason.message())
                .satisfies(thrown -> assertThat(((InternalAccountAuthenticationRejectedException) thrown).reason())
                        .isEqualTo(reason));
    }

    private static Fixture fixture() {
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        AccountGrantTypeResolver grants = new AccountGrantTypeResolver(
                List.of("password", "email-code", "authorization_code"));
        return new Fixture(roles, new InternalAccountEligibilityService(grants, roles));
    }

    private static InternalRegisteredClientPolicy policy(String clientId, String grantType) {
        return new InternalRegisteredClientPolicy("registered-" + clientId, clientId, grantType, Set.of("*:read"),
                Set.of("*:read"), "127.0.0.1/32", Duration.ofHours(3), Duration.ofHours(72));
    }

    private static AuthorizationAccount account(Long userId, Long roleId, Integer status, String extraGrantTypes) {
        return new AuthorizationAccount(userId, "alice", "stored-password-hash", "alice@example.test", roleId,
                extraGrantTypes, status);
    }

    private static RoleMetadata role(Long id, String roleCode, Integer rank) {
        return new RoleMetadata(id, roleCode, roleCode, rank, 1000, 1_073_741_824L,
                LocalDateTime.of(2026, 8, 10, 1, 2, 3), LocalDateTime.of(2026, 8, 10, 1, 2, 4));
    }

    private record Fixture(RoleMetadataRepository roles, InternalAccountEligibilityService service) {
    }
}
