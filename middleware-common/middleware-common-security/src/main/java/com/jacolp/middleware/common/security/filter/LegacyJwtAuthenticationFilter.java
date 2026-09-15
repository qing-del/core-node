package com.jacolp.middleware.common.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.json.JacksonObjectMapper;
import com.jacolp.middleware.common.core.metrics.QpsCounter;
import com.jacolp.middleware.common.security.context.AuthenticationContext;
import com.jacolp.middleware.common.security.context.AuthorizationContext;
import com.jacolp.middleware.common.security.context.SecurityContextBridge;
import com.jacolp.middleware.common.security.context.SecurityIdentity;
import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.middleware.common.security.jwt.JwtTokenSupport;
import com.jacolp.middleware.common.security.token.SecurityTokenConstants;
import com.jacolp.middleware.common.security.token.SecurityTokenKeyGenerator;
import com.jacolp.result.Result;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;

/** Preserves the legacy JWT MVC-interceptor contract while it runs in the security filter chain. */
public final class LegacyJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new JacksonObjectMapper();
    private static final PathPatternParser PATH_PATTERNS = new PathPatternParser();
    private static final PathPattern ADMIN = PATH_PATTERNS.parse("/admin/**");
    private static final PathPattern ADMIN_LOGIN = PATH_PATTERNS.parse("/admin/user/login");
    private static final PathPattern ADMIN_CALLBACK = PATH_PATTERNS.parse("/admin/audio/callback/**");
    private static final PathPattern USER = PATH_PATTERNS.parse("/user/**");
    private static final PathPattern USER_LOGIN = PATH_PATTERNS.parse("/user/user/login");
    private static final PathPattern USER_REGISTER = PATH_PATTERNS.parse("/user/user/register");
    private static final PathPattern USER_ACTIVE_CODE = PATH_PATTERNS.parse("/user/user/active-code");
    private static final PathPattern USER_RESEND = PATH_PATTERNS.parse("/user/user/resend-activation");
    private static final PathPattern USER_ACTIVE = PATH_PATTERNS.parse("/user/user/active/**");

    private final SecurityIdentity identity;
    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;
    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;
    private final QpsCounter qpsCounter;

    public LegacyJwtAuthenticationFilter(SecurityIdentity identity,
                                         ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider,
                                         StringRedisTemplate redis,
                                         JwtProperties jwtProperties,
                                         QpsCounter qpsCounter) {
        this.identity = identity;
        this.handlerMappingProvider = handlerMappingProvider;
        this.redis = redis;
        this.jwtProperties = jwtProperties;
        this.qpsCounter = qpsCounter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!matchesProtectedPath(request) || !targetsHandlerMethod(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            boolean authenticated;
            if (identity == SecurityIdentity.ACTIVATION) {
                authenticated = authenticateActivation(request, response);
            } else {
                authenticated = authenticateBearer(request, response);
            }
            if (authenticated) {
                filterChain.doFilter(request, response);
            }
        } finally {
            AuthenticationContext.clear();
            AuthorizationContext.clear();
            SecurityContextBridge.clear();
        }
    }

    boolean matchesProtectedPath(HttpServletRequest request) {
        String path = applicationPath(request);
        PathContainer candidate = PathContainer.parsePath(path);
        return switch (identity) {
            case ADMIN -> ADMIN.matches(candidate) && !ADMIN_LOGIN.matches(candidate) && !ADMIN_CALLBACK.matches(candidate);
            case USER -> USER.matches(candidate) && !USER_LOGIN.matches(candidate) && !USER_REGISTER.matches(candidate)
                    && !USER_ACTIVE_CODE.matches(candidate) && !USER_RESEND.matches(candidate) && !USER_ACTIVE.matches(candidate);
            case ACTIVATION -> USER_ACTIVE.matches(candidate);
        };
    }

    private boolean authenticateBearer(HttpServletRequest request, HttpServletResponse response) throws IOException {
        qpsCounter.increment();
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            if (identity == SecurityIdentity.ADMIN) {
                writeResult(response, Result.error("未提供认证令牌"));
            }
            return false;
        }
        try {
            Claims claims = JwtTokenSupport.parseJWT(secret(), token);
            Long id = Long.valueOf(claims.get(claim()).toString());
            String storedToken = redis.opsForValue().get(redisKey(id));
            if (storedToken == null || !storedToken.equals(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                writeResult(response, Result.error(identity == SecurityIdentity.ADMIN
                        ? "认证令牌已过期" : "认证令牌无效或已过期"));
                return false;
            }
            SecurityContextBridge.authenticate(id, identity);
            return true;
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeResult(response, Result.error("认证令牌无效或已过期"));
            return false;
        }
    }

    private boolean authenticateActivation(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = request.getRequestURI().split("/");
        String token = parts[parts.length - 1];
        try {
            Claims claims = JwtTokenSupport.parseJWT(jwtProperties.getActiveSecretKey(), token);
            Long userId = Long.valueOf(claims.get(SecurityTokenConstants.USER_ID_CLAIM).toString());
            boolean activeCode = Boolean.parseBoolean(claims.get(SecurityTokenConstants.ACTIVE_SIGN_KEY).toString());
            if (!activeCode) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                writeResult(response, Result.error("并激活令牌，无法激活账号"));
                return false;
            }
            SecurityContextBridge.authenticate(userId, SecurityIdentity.ACTIVATION);
            return true;
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeResult(response, Result.error("认证令牌无效或已过期"));
            return false;
        }
    }

    private boolean targetsHandlerMethod(HttpServletRequest request) {
        RequestMappingHandlerMapping mapping;
        try {
            mapping = handlerMappingProvider.getIfAvailable();
        } catch (Exception ignored) {
            return false;
        }
        if (mapping == null) {
            return false;
        }
        try {
            HandlerExecutionChain chain = mapping.getHandler(request);
            return chain != null && chain.getHandler() instanceof HandlerMethod;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String secret() {
        return identity == SecurityIdentity.ADMIN ? jwtProperties.getAdminSecretKey() : jwtProperties.getUserSecretKey();
    }

    private String claim() {
        return identity == SecurityIdentity.ADMIN
                ? SecurityTokenConstants.ADMIN_ID_CLAIM : SecurityTokenConstants.USER_ID_CLAIM;
    }

    private String redisKey(Long id) {
        return identity == SecurityIdentity.ADMIN
                ? SecurityTokenKeyGenerator.getAdminLoginKey(id) : SecurityTokenKeyGenerator.getUserLoginKey(id);
    }

    private static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
    }

    private static void writeResult(HttpServletResponse response, Result<?> result) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), result);
    }
}
