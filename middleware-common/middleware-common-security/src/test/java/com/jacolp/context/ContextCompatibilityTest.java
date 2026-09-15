package com.jacolp.context;

import com.jacolp.middleware.common.security.context.AuthenticationContext;
import com.jacolp.middleware.common.security.context.AuthorizationContext;
import com.jacolp.middleware.common.security.context.SecurityContextBridge;
import com.jacolp.middleware.common.security.context.SecurityIdentity;
import com.jacolp.exception.AuthenticationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCompatibilityTest {

    @AfterEach
    void clearContexts() {
        BaseContext.remove();
        PermissionContext.remove();
        SecurityContextBridge.clear();
    }

    @Test
    void newSecurityContextIsVisibleThroughLegacyFacade() {
        SecurityContextBridge.authenticate(101L, SecurityIdentity.ADMIN);

        assertThat(BaseContext.getCurrentId()).isEqualTo(101L);
        assertThat(PermissionContext.isAdmin()).isTrue();
    }

    @Test
    void legacyFacadeWritesToNewSecurityContext() {
        BaseContext.setCurrentId(202L);
        PermissionContext.setAdmin(false);

        assertThat(AuthenticationContext.getCurrentId()).isEqualTo(202L);
        assertThat(AuthorizationContext.isAdmin()).isFalse();
    }

    @Test
    void holderTakesPrecedenceAndMapsAllAuthorities() {
        AuthenticationContext.setCurrentId(9L);
        AuthorizationContext.setAdmin(true);
        SecurityContextBridge.authenticate(101L, SecurityIdentity.USER);
        assertThat(BaseContext.getCurrentId()).isEqualTo(101L);
        assertThat(PermissionContext.isAdmin()).isFalse();
        SecurityContextBridge.authenticate(102L, SecurityIdentity.ACTIVATION);
        assertThat(PermissionContext.isAdmin()).isFalse();
        SecurityContextBridge.authenticate(103L, SecurityIdentity.ADMIN);
        assertThat(PermissionContext.isAdmin()).isTrue();
    }

    @Test
    void absentHolderFallsBackAndAbsentIdentityRetainsLegacyException() {
        BaseContext.setCurrentId(88L);
        PermissionContext.setAdmin(true);
        assertThat(BaseContext.getCurrentIdWithoutValid()).isEqualTo(88L);
        assertThat(PermissionContext.isAdmin()).isTrue();
        BaseContext.remove();
        org.assertj.core.api.Assertions.assertThatThrownBy(BaseContext::getCurrentId)
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("当前登录信息已失效");
    }

    @Test
    void holderAuthorityOverridesPrincipalIdentity() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new com.jacolp.middleware.common.security.context.SecurityPrincipal(1L, SecurityIdentity.ADMIN), null,
                List.of(new SimpleGrantedAuthority(SecurityIdentity.USER.authority()))));
        assertThat(PermissionContext.isAdmin()).isFalse();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new com.jacolp.middleware.common.security.context.SecurityPrincipal(2L, SecurityIdentity.USER), null,
                List.of(new SimpleGrantedAuthority(SecurityIdentity.ADMIN.authority()))));
        assertThat(PermissionContext.isAdmin()).isTrue();
    }
}
