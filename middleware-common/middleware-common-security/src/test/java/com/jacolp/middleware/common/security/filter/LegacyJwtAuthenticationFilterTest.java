package com.jacolp.middleware.common.security.filter;

import com.jacolp.middleware.common.core.metrics.QpsCounter;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.middleware.common.security.context.AuthenticationContext;
import com.jacolp.middleware.common.security.context.AuthorizationContext;
import com.jacolp.middleware.common.security.context.SecurityIdentity;
import com.jacolp.middleware.common.security.context.SecurityPrincipal;
import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.middleware.common.security.jwt.JwtTokenSupport;
import com.jacolp.middleware.common.security.token.SecurityTokenConstants;
import com.jacolp.middleware.common.security.token.SecurityTokenKeyGenerator;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyJwtAuthenticationFilterTest {

    private static final String USER_SECRET = "user-secret-key-for-characterization";
    private static final String ADMIN_SECRET = "admin-secret-key-for-characterization";
    private static final String ACTIVE_SECRET = "active-secret-key-for-characterization";
    private static final long USER_ID = 101L;

    private ObjectProvider<RequestMappingHandlerMapping> mappingProvider;
    private RequestMappingHandlerMapping mapping;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private QpsCounter qpsCounter;
    private JwtProperties jwtProperties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        mappingProvider = mock(ObjectProvider.class);
        mapping = mock(RequestMappingHandlerMapping.class);
        when(mappingProvider.getIfAvailable()).thenReturn(mapping);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        qpsCounter = mock(QpsCounter.class);
        jwtProperties = new JwtProperties();
        jwtProperties.setUserSecretKey(USER_SECRET);
        jwtProperties.setAdminSecretKey(ADMIN_SECRET);
        jwtProperties.setActiveSecretKey(ACTIVE_SECRET);
    }

    @AfterEach
    void clearContexts() {
        AuthenticationContext.clear();
        AuthorizationContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void userFilterAuthenticatesHandlerMethodThenCleansAllContexts() throws Exception {
        LegacyJwtAuthenticationFilter filter = filter(SecurityIdentity.USER);
        String token = userToken(USER_ID);
        when(values.get(SecurityTokenKeyGenerator.getUserLoginKey(USER_ID))).thenReturn(token);
        MockHttpServletRequest request = bearerRequest("/user/note/list", token);
        handlerMethod(request);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(BaseContext.getCurrentId()).isEqualTo(USER_ID);
            assertThat(PermissionContext.isAdmin()).isFalse();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(new SecurityPrincipal(USER_ID, SecurityIdentity.USER));
            return null;
        }).when(chain).doFilter(eq(request), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        verify(qpsCounter).increment();
        verify(chain).doFilter(eq(request), eq(response));
        assertThat(AuthenticationContext.getCurrentIdWithoutValidation()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void userFilterPreservesMissingTokenStatusWithoutResponseBody() throws Exception {
        LegacyJwtAuthenticationFilter filter = filter(SecurityIdentity.USER);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/note/list");
        handlerMethod(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verify(chain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void adminFilterKeepsRedisMismatchMessage() throws Exception {
        LegacyJwtAuthenticationFilter filter = filter(SecurityIdentity.ADMIN);
        String token = adminToken(7L);
        when(values.get(SecurityTokenKeyGenerator.getAdminLoginKey(7L))).thenReturn("other-token");
        MockHttpServletRequest request = bearerRequest("/admin/note/list", token);
        handlerMethod(request);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("认证令牌已过期");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void adminFilterRejectsMissingTokenAndAuthenticatesValidAdmin() throws Exception {
        LegacyJwtAuthenticationFilter filter = filter(SecurityIdentity.ADMIN);
        MockHttpServletRequest missing = new MockHttpServletRequest("GET", "/admin/note/list");
        handlerMethod(missing);
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilter(missing, missingResponse, mock(FilterChain.class));
        assertThat(missingResponse.getContentAsString()).contains("未提供认证令牌");

        String token = adminToken(7L);
        when(values.get(SecurityTokenKeyGenerator.getAdminLoginKey(7L))).thenReturn(token);
        MockHttpServletRequest valid = bearerRequest("/admin/note/list", token);
        handlerMethod(valid);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(PermissionContext.isAdmin()).isTrue();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(new SecurityPrincipal(7L, SecurityIdentity.ADMIN));
            return null;
        }).when(chain).doFilter(eq(valid), any());
        filter.doFilter(valid, new MockHttpServletResponse(), chain);
    }

    @Test
    void userFilterRejectsInvalidJwtAndRedisMissingToken() throws Exception {
        LegacyJwtAuthenticationFilter filter = filter(SecurityIdentity.USER);
        MockHttpServletRequest invalid = bearerRequest("/user/note/list", "invalid-token");
        handlerMethod(invalid);
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        filter.doFilter(invalid, invalidResponse, mock(FilterChain.class));
        assertThat(invalidResponse.getContentAsString()).contains("认证令牌无效或已过期");

        String token = userToken(USER_ID);
        MockHttpServletRequest absent = bearerRequest("/user/note/list", token);
        handlerMethod(absent);
        MockHttpServletResponse absentResponse = new MockHttpServletResponse();
        filter.doFilter(absent, absentResponse, mock(FilterChain.class));
        assertThat(absentResponse.getContentAsString()).contains("认证令牌无效或已过期");
    }

    @Test
    void activationFilterUsesLastPathTokenAndRole() throws Exception {
        LegacyJwtAuthenticationFilter filter = filter(SecurityIdentity.ACTIVATION);
        String token = activationToken(USER_ID, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/user/active/" + token);
        handlerMethod(request);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(new SecurityPrincipal(USER_ID, SecurityIdentity.ACTIVATION));
            return null;
        }).when(chain).doFilter(eq(request), any());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(qpsCounter, never()).increment();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void activationFilterRejectsFalseFlagInvalidAndTrailingSlashWithLegacyMessages() throws Exception {
        LegacyJwtAuthenticationFilter filter = filter(SecurityIdentity.ACTIVATION);
        MockHttpServletRequest falseToken = new MockHttpServletRequest("GET", "/user/user/active/" + activationToken(USER_ID, false));
        handlerMethod(falseToken);
        MockHttpServletResponse falseResponse = new MockHttpServletResponse();
        filter.doFilter(falseToken, falseResponse, mock(FilterChain.class));
        assertThat(falseResponse.getContentAsString()).contains("并激活令牌，无法激活账号");

        MockHttpServletRequest invalid = new MockHttpServletRequest("GET", "/user/user/active/invalid");
        handlerMethod(invalid);
        MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
        filter.doFilter(invalid, invalidResponse, mock(FilterChain.class));
        assertThat(invalidResponse.getContentAsString()).contains("认证令牌无效或已过期");

        MockHttpServletRequest trailing = new MockHttpServletRequest("GET", "/user/user/active/");
        handlerMethod(trailing);
        MockHttpServletResponse trailingResponse = new MockHttpServletResponse();
        filter.doFilter(trailing, trailingResponse, mock(FilterChain.class));
        assertThat(trailingResponse.getContentAsString()).contains("认证令牌无效或已过期");
    }

    @Test
    void nonHandlerAndPathExclusionsBypassAuthentication() throws Exception {
        LegacyJwtAuthenticationFilter user = filter(SecurityIdentity.USER);
        MockHttpServletRequest staticRequest = new MockHttpServletRequest("GET", "/user/image/file");
        when(mapping.getHandler(staticRequest)).thenReturn(new HandlerExecutionChain(new Object()));
        FilterChain chain = mock(FilterChain.class);
        user.doFilter(staticRequest, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(eq(staticRequest), any());
        verify(qpsCounter, never()).increment();

        assertThat(user.matchesProtectedPath(new MockHttpServletRequest("POST", "/user/user/login"))).isFalse();
        assertThat(filter(SecurityIdentity.ADMIN).matchesProtectedPath(
                new MockHttpServletRequest("POST", "/admin/user/login"))).isFalse();
        assertThat(filter(SecurityIdentity.ACTIVATION).matchesProtectedPath(
                new MockHttpServletRequest("GET", "/user/user/active/token"))).isTrue();
        assertThat(filter(SecurityIdentity.ACTIVATION).matchesProtectedPath(
                new MockHttpServletRequest("GET", "/user/user/active"))).isTrue();
        assertThat(filter(SecurityIdentity.ADMIN).matchesProtectedPath(
                new MockHttpServletRequest("GET", "/admin"))).isTrue();
        assertThat(filter(SecurityIdentity.ADMIN).matchesProtectedPath(
                new MockHttpServletRequest("GET", "/admin/audio/callback"))).isFalse();
        MockHttpServletRequest contextual = new MockHttpServletRequest("GET", "/app/user/note/list");
        contextual.setContextPath("/app");
        assertThat(user.matchesProtectedPath(contextual)).isTrue();
    }

    private LegacyJwtAuthenticationFilter filter(SecurityIdentity identity) {
        return new LegacyJwtAuthenticationFilter(identity, mappingProvider, redis, jwtProperties, qpsCounter);
    }

    private void handlerMethod(MockHttpServletRequest request) throws Exception {
        Method method = HandlerTarget.class.getDeclaredMethod("handle");
        when(mapping.getHandler(request)).thenReturn(new HandlerExecutionChain(new HandlerMethod(new HandlerTarget(), method)));
    }

    private static MockHttpServletRequest bearerRequest(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String userToken(long id) {
        return JwtTokenSupport.createJWT(USER_SECRET, 60_000,
                new HashMap<>(Map.of(SecurityTokenConstants.USER_ID_CLAIM, id)));
    }

    private static String adminToken(long id) {
        return JwtTokenSupport.createJWT(ADMIN_SECRET, 60_000,
                new HashMap<>(Map.of(SecurityTokenConstants.ADMIN_ID_CLAIM, id)));
    }

    private static String activationToken(long id, boolean active) {
        return JwtTokenSupport.createJWT(ACTIVE_SECRET, 60_000, new HashMap<>(Map.of(
                SecurityTokenConstants.USER_ID_CLAIM, id, SecurityTokenConstants.ACTIVE_SIGN_KEY, active)));
    }

    private static final class HandlerTarget {
        public void handle() {
        }
    }
}
