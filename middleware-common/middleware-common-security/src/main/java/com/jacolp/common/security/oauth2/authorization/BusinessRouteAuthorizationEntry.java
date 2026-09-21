package com.jacolp.common.security.oauth2.authorization;

import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 一条精确的 HTTP 方法/路径授权规则。
 *
 * <p>规则可以要求调用方拥有全部 scope，也可以通过 {@link #anyRequiredScope()}
 * 表示只需满足其中一个 scope。路径模式会在构造时预先校验，避免无效规则进入运行时目录。</p>
 */
public record BusinessRouteAuthorizationEntry(HttpMethod method, String pathPattern,
                                              Set<String> requiredScopes, String requiredClientId,
                                              boolean anyRequiredScope) {

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    public BusinessRouteAuthorizationEntry {
        method = Objects.requireNonNull(method, "method must not be null");
        if (!StringUtils.hasText(pathPattern) || !pathPattern.startsWith("/")) {
            // 路由目录只接受绝对路径；空路径或相对路径无法与 Spring 请求路径安全匹配。
            throw new IllegalArgumentException("pathPattern must be an absolute Spring path pattern");
        }
        // 在规则创建阶段编译路径，尽早暴露非法 Spring PathPattern，而不是等到首个请求才失败。
        compiledPattern(pathPattern);
        requiredScopes = immutableScopes(requiredScopes);
        if (!"user".equals(requiredClientId) && !"admin".equals(requiredClientId)) {
            // 业务路由只允许已定义的两个 OAuth client，防止目录出现未被安全链路处理的 client。
            throw new IllegalArgumentException("requiredClientId must be user or admin");
        }
    }

    public BusinessRouteAuthorizationEntry(HttpMethod method, String pathPattern,
                                           Set<String> requiredScopes, String requiredClientId) {
        this(method, pathPattern, requiredScopes, requiredClientId, false);
    }

    /** 返回已校验的路径模式，用于匹配请求而不重复暴露解析细节。 */
    PathPattern compiledPattern() {
        return compiledPattern(pathPattern);
    }

    /** 编译单条 Spring 路径模式，并把底层解析异常转换为目录配置错误。 */
    private static PathPattern compiledPattern(String pathPattern) {
        try {
            return PATH_PATTERN_PARSER.parse(pathPattern);
        } catch (RuntimeException exception) {
            // 将配置错误包装成统一的 IllegalArgumentException，便于启动阶段定位具体路由。
            throw new IllegalArgumentException("pathPattern must be a valid Spring path pattern", exception);
        }
    }

    /** 校验 scope 非空、合法且无重复，并返回不可变副本避免运行时修改规则。 */
    private static Set<String> immutableScopes(Set<String> scopes) {
        Objects.requireNonNull(scopes, "requiredScopes must not be null");
        if (scopes.isEmpty()) {
            // 没有任何 scope 的规则会退化为仅凭路径放行，违反业务路由的最小权限约束。
            throw new IllegalArgumentException("requiredScopes must not be empty");
        }
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String scope : scopes) {
            // PermissionScopeMatcher 同时校验 scope 的格式，确保目录与 JWT scope 语义一致。
            PermissionScopeMatcher.grants(Set.of(scope), scope);
            if (!canonical.add(scope)) {
                // 重复 scope 通常意味着路由定义笔误，启动时直接失败比静默去重更容易发现问题。
                throw new IllegalArgumentException("requiredScopes must not contain duplicates");
            }
        }
        return Set.copyOf(canonical);
    }
}
