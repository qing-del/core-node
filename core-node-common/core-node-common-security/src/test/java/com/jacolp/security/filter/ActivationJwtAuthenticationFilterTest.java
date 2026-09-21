package com.jacolp.security.filter;

import com.jacolp.common.security.filter.ActivationJwtAuthenticationFilter;
import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.common.security.context.SecurityIdentity;
import com.jacolp.common.security.context.SecurityPrincipal;
import com.jacolp.common.security.activation.ActivationJwtTokenSupport;
import com.jacolp.common.security.jwt.JwtProperties;
import com.jacolp.common.security.token.SecurityTokenConstants;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivationJwtAuthenticationFilterTest {

    private static final String ACTIVE_SECRET = "active-secret-key-for-characterization";
    private static final long USER_ID = 101L;

    private ObjectProvider<RequestMappingHandlerMapping> mappingProvider;
    private RequestMappingHandlerMapping mapping;
    private JwtProperties jwtProperties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mappingProvider = mock(ObjectProvider.class);
        mapping = mock(RequestMappingHandlerMapping.class);
        when(mappingProvider.getIfAvailable()).thenReturn(mapping);
        jwtProperties = new JwtProperties();
        jwtProperties.setActiveSecretKey(ACTIVE_SECRET);
    }

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesActivationLinkAndExposesTheActivationPrincipalForTheHandler() throws Exception {
        ActivationJwtAuthenticationFilter filter = filter();
        String token = activationToken(USER_ID, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/user/active/" + token);
        handlerMethod(request);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(BaseContext.getCurrentId()).isEqualTo(USER_ID);
            assertThat(PermissionContext.isAdmin()).isFalse();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(new SecurityPrincipal(USER_ID, SecurityIdentity.ACTIVATION));
            return null;
        }).when(chain).doFilter(eq(request), any());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(eq(request), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsFalseActivationClaimInvalidTokenAndTrailingSlashWithTheLegacyResponses() throws Exception {
        ActivationJwtAuthenticationFilter filter = filter();
        MockHttpServletRequest falseToken = new MockHttpServletRequest("GET", "/user/user/active/" + activationToken(USER_ID, false));
        handlerMethod(falseToken);
        MockHttpServletResponse falseResponse = new MockHttpServletResponse();
        filter.doFilter(falseToken, falseResponse, mock(FilterChain.class));
        assertThat(falseResponse.getStatus()).isEqualTo(401);
        assertThat(falseResponse.getContentAsString()).contains("并激活令牌，无法激活账号");

        MockHttpServletRequest invalid = new MockHttpServletRequest("GET", "/user/user/active/invalid");
        handlerMethod(invalid);
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        filter.doFilter(invalid, invalidResponse, mock(FilterChain.class));
        assertThat(invalidResponse.getStatus()).isEqualTo(401);
        assertThat(invalidResponse.getContentAsString()).contains("认证令牌无效或已过期");

        MockHttpServletRequest trailing = new MockHttpServletRequest("GET", "/user/user/active/");
        handlerMethod(trailing);
        MockHttpServletResponse trailingResponse = new MockHttpServletResponse();
        filter.doFilter(trailing, trailingResponse, mock(FilterChain.class));
        assertThat(trailingResponse.getContentAsString()).contains("认证令牌无效或已过期");
    }

    @Test
    void onlyMappedActivationRoutesAreAuthenticatedAndContextPathsAreSupported() throws Exception {
        ActivationJwtAuthenticationFilter filter = filter();
        MockHttpServletRequest staticRequest = new MockHttpServletRequest("GET", "/user/user/active/token");
        when(mapping.getHandler(staticRequest)).thenReturn(new HandlerExecutionChain(new Object()));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(staticRequest, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(eq(staticRequest), any());
        assertThat(filter.matchesActivationPath(new MockHttpServletRequest("GET", "/user/user/active/token"))).isTrue();
        assertThat(filter.matchesActivationPath(new MockHttpServletRequest("GET", "/user/user/active"))).isTrue();
        MockHttpServletRequest contextual = new MockHttpServletRequest("GET", "/app/user/user/active/token");
        contextual.setContextPath("/app");
        assertThat(filter.matchesActivationPath(contextual)).isTrue();
    }

    private ActivationJwtAuthenticationFilter filter() {
        return new ActivationJwtAuthenticationFilter(mappingProvider, jwtProperties);
    }

    private void handlerMethod(MockHttpServletRequest request) throws Exception {
        Method method = HandlerTarget.class.getDeclaredMethod("handle");
        when(mapping.getHandler(request)).thenReturn(new HandlerExecutionChain(new HandlerMethod(new HandlerTarget(), method)));
    }

    private static String activationToken(long id, boolean active) {
        return ActivationJwtTokenSupport.createActivationJwt(ACTIVE_SECRET, 60_000, new HashMap<>(Map.of(
                SecurityTokenConstants.USER_ID_CLAIM, id, SecurityTokenConstants.ACTIVE_SIGN_KEY, active)));
    }

    private static final class HandlerTarget {
        public void handle() {
        }
    }
}
