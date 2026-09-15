package com.jacolp.security.oauth2.authorization;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationEntry;
import com.jacolp.common.security.oauth2.authorization.ImmutableBusinessRouteAuthorizationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Set;

import static com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy.Decision.ALLOW;
import static com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy.Decision.CLIENT_MISMATCH;
import static com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy.Decision.NO_MATCH;
import static com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy.Decision.ROLE_MISMATCH;
import static com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy.Decision.SCOPE_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImmutableBusinessRouteAuthorizationPolicyTest {

    private final ImmutableBusinessRouteAuthorizationPolicy policy = new ImmutableBusinessRouteAuthorizationPolicy(List.of(
            entry(HttpMethod.GET, "/user/note/{id}", Set.of("note:read"), "user"),
            entry(HttpMethod.PUT, "/user/note/relation/image/bind", Set.of("note:write", "media:read"), "user"),
            entry(HttpMethod.GET, "/admin/image/notes/{id}", Set.of("media:read", "note:read"), "admin")
    ));

    @Test
    void allowsExactAndWildcardScopesOnlyWhenEveryRequiredScopeIsGranted() {
        assertThat(policy.authorize(HttpMethod.PUT, "/user/note/relation/image/bind",
                principal("user", List.of("USER"), List.of("note:write", "media:read")))).isEqualTo(ALLOW);
        assertThat(policy.authorize(HttpMethod.PUT, "/user/note/relation/image/bind",
                principal("user", List.of("USER"), List.of("*:write", "*:read")))).isEqualTo(ALLOW);
        assertThat(policy.authorize(HttpMethod.PUT, "/user/note/relation/image/bind",
                principal("user", List.of("USER"), List.of("note:write")))).isEqualTo(SCOPE_MISMATCH);
    }

    @Test
    void enforcesClientBoundaryAndAllowsManagementRolesOnUserClient() {
        assertThat(policy.authorize(HttpMethod.GET, "/user/note/7",
                principal("admin", List.of("ADMIN"), List.of("note:read")))).isEqualTo(CLIENT_MISMATCH);
        assertThat(policy.authorize(HttpMethod.GET, "/user/note/7",
                principal("user", List.of("ADMIN"), List.of("note:read")))).isEqualTo(ALLOW);
        assertThat(policy.authorize(HttpMethod.GET, "/user/note/7",
                principal("user", List.of("CREATOR"), List.of("note:read")))).isEqualTo(ALLOW);
        assertThat(policy.authorize(HttpMethod.GET, "/admin/image/notes/7",
                principal("user", List.of("CREATOR"), List.of("media:read", "note:read")))).isEqualTo(CLIENT_MISMATCH);
        assertThat(policy.authorize(HttpMethod.GET, "/admin/image/notes/7",
                principal("admin", List.of("USER"), List.of("media:read", "note:read")))).isEqualTo(ROLE_MISMATCH);
        assertThat(policy.authorize(HttpMethod.GET, "/user/note/7",
                principal("core_agent", List.of("USER"), List.of("note:read")))).isEqualTo(CLIENT_MISMATCH);
    }

    @Test
    void usesSpringPathPatternsAndHttpMethodAsPartOfTheRouteIdentity() {
        assertThat(policy.authorize(HttpMethod.GET, "/user/note/123",
                principal("user", List.of("USER"), List.of("note:read")))).isEqualTo(ALLOW);
        assertThat(policy.authorize(HttpMethod.POST, "/user/note/123",
                principal("user", List.of("USER"), List.of("note:read")))).isEqualTo(NO_MATCH);
        assertThat(policy.authorize(HttpMethod.GET, "/user/missing",
                principal("user", List.of("USER"), List.of("note:read")))).isEqualTo(NO_MATCH);
    }

    @Test
    void prefersTheSpringPathPatternWithHigherSpecificityAndFailsClosedOnATie() {
        ImmutableBusinessRouteAuthorizationPolicy specificityPolicy = new ImmutableBusinessRouteAuthorizationPolicy(List.of(
                entry(HttpMethod.GET, "/user/note/{id}", Set.of("note:read"), "user"),
                entry(HttpMethod.GET, "/user/note/search", Set.of("note:write"), "user")
        ));
        assertThat(specificityPolicy.authorize(HttpMethod.GET, "/user/note/search",
                principal("user", List.of("USER"), List.of("note:write")))).isEqualTo(ALLOW);

        ImmutableBusinessRouteAuthorizationPolicy ambiguousPolicy = new ImmutableBusinessRouteAuthorizationPolicy(List.of(
                entry(HttpMethod.GET, "/user/note/{id}", Set.of("note:read"), "user"),
                entry(HttpMethod.GET, "/user/note/{slug}", Set.of("note:write"), "user")
        ));
        assertThatThrownBy(() -> ambiguousPolicy.authorize(HttpMethod.GET, "/user/note/7",
                principal("user", List.of("USER"), List.of("note:read", "note:write"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void entriesAndPolicyAreImmutableAndRejectDuplicateRouteDefinitions() {
        assertThatThrownBy(() -> policy.entries().add(entry(HttpMethod.GET, "/user/x", Set.of("note:read"), "user")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ImmutableBusinessRouteAuthorizationPolicy(List.of(
                entry(HttpMethod.GET, "/user/note/{id}", Set.of("note:read"), "user"),
                entry(HttpMethod.GET, "/user/note/{id}", Set.of("note:write"), "user")
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> entry(HttpMethod.GET, "not/a/path", Set.of("note:read"), "user"));
    }

    private static BusinessRouteAuthorizationEntry entry(HttpMethod method, String path, Set<String> scopes, String client) {
        return new BusinessRouteAuthorizationEntry(method, path, scopes, client);
    }

    private static CurrentPrincipal principal(String clientId, List<String> roles, List<String> scopes) {
        return new CurrentPrincipal(7L, "alice", clientId, "password", roles, scopes);
    }
}
