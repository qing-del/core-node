package com.jacolp.common.security.oauth2.authorization;

import com.jacolp.common.security.context.CurrentPrincipal;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies a validated, immutable route catalogue. A request matching more than one rule is denied
 * at construction time rather than relying on rule order.
 */
public final class ImmutableBusinessRouteAuthorizationPolicy implements BusinessRouteAuthorizationPolicy {

    private static final Set<String> USER_CLIENT_ROLES = Set.of("USER", "ADMIN", "CREATOR");

    private final List<BusinessRouteAuthorizationEntry> entries;

    /**
     * 创建不可变路由授权策略，并在启动阶段拒绝同方法同路径的重复规则。
     *
     * @param entries 已完成字段校验的业务路由目录
     */
    public ImmutableBusinessRouteAuthorizationPolicy(Collection<BusinessRouteAuthorizationEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        this.entries = List.copyOf(entries);
        if (this.entries.isEmpty()) {
            // 空目录会让所有请求落入 NO_MATCH，通常表示配置没有被正确加载，因此拒绝启动。
            throw new IllegalArgumentException("entries must not be empty");
        }
        rejectDuplicateMethodPatterns(this.entries);
    }

    /** 返回不可变的路由规则快照，供过滤器和测试复用。 */
    public List<BusinessRouteAuthorizationEntry> entries() {
        return entries;
    }

    /**
     * 按方法、路径、client、角色和 scope 顺序判定一次业务请求。
     *
     * <p>同一请求若命中多个同等具体度规则会视为配置歧义并抛错，避免依赖列表顺序产生
     * 不可预测的授权结果。</p>
     */
    @Override
    public Decision authorize(HttpMethod method, String requestPath, CurrentPrincipal principal) {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        PathContainer path = PathContainer.parsePath(requireAbsolutePath(requestPath));
        List<BusinessRouteAuthorizationEntry> matches = entries.stream()
                .filter(entry -> entry.method() == method && entry.compiledPattern().matches(path))
                .toList();
        if (matches.isEmpty()) {
            // 没有目录规则覆盖该请求时交由其他安全链处理，而不是在这里猜测其权限。
            return Decision.NO_MATCH;
        }
        List<BusinessRouteAuthorizationEntry> orderedMatches = matches.stream()
                .sorted((left, right) -> org.springframework.web.util.pattern.PathPattern.SPECIFICITY_COMPARATOR
                        .compare(left.compiledPattern(), right.compiledPattern()))
                .toList();
        if (orderedMatches.size() > 1
                && org.springframework.web.util.pattern.PathPattern.SPECIFICITY_COMPARATOR.compare(
                orderedMatches.getFirst().compiledPattern(), orderedMatches.get(1).compiledPattern()) == 0) {
            // 两条同等具体度规则无法安全决定优先级，拒绝请求并暴露目录配置问题。
            throw new IllegalStateException("business route catalogue has ambiguous overlapping matches");
        }

        BusinessRouteAuthorizationEntry entry = orderedMatches.getFirst();
        if (!entry.requiredClientId().equals(principal.clientId())) {
            // client 不匹配时即使 scope 相同也不能访问，避免不同 OAuth client 共享业务入口。
            return Decision.CLIENT_MISMATCH;
        }
        if (!clientRoleMatches(entry.requiredClientId(), principal.roles())) {
            // 角色约束独立于 scope 校验，防止 user/admin client 仅凭 scope 越过角色边界。
            return Decision.ROLE_MISMATCH;
        }
        // any-of 规则用于 document:read/document:write 等互斥能力；普通规则仍要求全部 scope。
        boolean scopeGranted = entry.anyRequiredScope()
                ? entry.requiredScopes().stream().anyMatch(scope -> PermissionScopeMatcher.grants(principal.scopes(), scope))
                : PermissionScopeMatcher.grantsAll(principal.scopes(), entry.requiredScopes());
        // 将 scope 结果转换为统一决策值，调用方无需了解 any-of/all-of 的内部实现。
        return scopeGranted ? Decision.ALLOW : Decision.SCOPE_MISMATCH;
    }

    /** 检查目录中是否存在同 HTTP 方法和同路径模式的重复定义。 */
    private static void rejectDuplicateMethodPatterns(List<BusinessRouteAuthorizationEntry> entries) {
        List<String> duplicates = new ArrayList<>();
        for (int left = 0; left < entries.size(); left++) {
            for (int right = left + 1; right < entries.size(); right++) {
                BusinessRouteAuthorizationEntry first = entries.get(left);
                BusinessRouteAuthorizationEntry second = entries.get(right);
                if (first.method() == second.method() && first.pathPattern().equals(second.pathPattern())) {
                    // 重复规则会让后续授权结果依赖配置顺序，因此先收集后统一报告。
                    duplicates.add(first.method() + " " + first.pathPattern());
                }
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate business route entries: " + duplicates);
        }
    }

    /** 判断 client 对应的角色集合是否满足业务路由的最小角色要求。 */
    private static boolean clientRoleMatches(String clientId, List<String> roles) {
        return switch (clientId) {
            case "user" -> roles.size() == 1 && USER_CLIENT_ROLES.contains(roles.getFirst());
            case "admin" -> roles.equals(List.of("ADMIN")) || roles.equals(List.of("CREATOR"));
            default -> false;
        };
    }

    /** 校验请求路径是绝对路径，避免空值或相对路径绕过路径匹配。 */
    private static String requireAbsolutePath(String requestPath) {
        if (requestPath == null || !requestPath.startsWith("/")) {
            // 授权策略只处理 Servlet 绝对路径；非法输入不应被当作未匹配请求放行。
            throw new IllegalArgumentException("requestPath must be absolute");
        }
        return requestPath;
    }
}
