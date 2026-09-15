package com.jacolp.common.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.core.json.JacksonObjectMapper;
import com.jacolp.common.security.activation.ActivationJwtTokenSupport;
import com.jacolp.common.security.context.SecurityIdentity;
import com.jacolp.common.security.context.SecurityPrincipal;
import com.jacolp.common.core.result.Result;
import com.jacolp.common.security.jwt.JwtProperties;
import com.jacolp.common.security.token.SecurityTokenConstants;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;

/** Authenticates the activation-link JWT exception that remains outside the OAuth2 token flow. */
public final class ActivationJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new JacksonObjectMapper();
    private static final PathPattern ACTIVE = new PathPatternParser().parse("/user/user/active/**");

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;
    private final JwtProperties jwtProperties;

    public ActivationJwtAuthenticationFilter(ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider,
                                             JwtProperties jwtProperties) {
        this.handlerMappingProvider = handlerMappingProvider;
        this.jwtProperties = jwtProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!matchesActivationPath(request) || !targetsHandlerMethod(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (authenticateActivation(request, response)) {
                filterChain.doFilter(request, response);
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public boolean matchesActivationPath(HttpServletRequest request) {
        return ACTIVE.matches(PathContainer.parsePath(applicationPath(request)));
    }

    private boolean authenticateActivation(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = request.getRequestURI().split("/");
        String token = parts[parts.length - 1];
        try {
            Claims claims = ActivationJwtTokenSupport.parseActivationJwt(jwtProperties.getActiveSecretKey(), token);
            Long userId = Long.valueOf(claims.get(SecurityTokenConstants.USER_ID_CLAIM).toString());
            boolean activeCode = Boolean.parseBoolean(claims.get(SecurityTokenConstants.ACTIVE_SIGN_KEY).toString());
            if (!activeCode) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                writeResult(response, Result.error("并激活令牌，无法激活账号"));
                return false;
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new SecurityPrincipal(userId, SecurityIdentity.ACTIVATION), null,
                    List.of(new SimpleGrantedAuthority(SecurityIdentity.ACTIVATION.authority()))));
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
