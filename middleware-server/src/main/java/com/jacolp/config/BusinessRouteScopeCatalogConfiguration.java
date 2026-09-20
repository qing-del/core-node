package com.jacolp.config;

import com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationEntry;
import com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy;
import com.jacolp.common.security.oauth2.authorization.ImmutableBusinessRouteAuthorizationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The executable counterpart of static/document/security/phase5-business-route-scope-catalog.md. */
@Configuration
public class BusinessRouteScopeCatalogConfiguration {

    private static final String INTERNAL_LOGOUT_PATH = "/auth/logout";

    /** 将静态路由目录构造成启动后不可变的授权策略。 */
    @Bean
    public BusinessRouteAuthorizationPolicy businessRouteAuthorizationPolicy() {
        return new ImmutableBusinessRouteAuthorizationPolicy(entries());
    }

    /** 将目录中的每条方法/路径规则组合为资源服务器需要的请求匹配器；不包含公共激活例外。 */
    @Bean
    public RequestMatcher businessRouteRequestMatcher() {
        return new OrRequestMatcher(entries().stream()
                .<RequestMatcher>map(entry -> PathPatternRequestMatcher.pathPattern(entry.method(), entry.pathPattern()))
                .toList());
    }

    /** 匹配资源服务器链路内部的登出接口；该接口不计入业务目录统计。 */
    @Bean
    public RequestMatcher internalLogoutRequestMatcher() {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.POST, INTERNAL_LOGOUT_PATH);
    }

    /** 合并业务路由和内部登出匹配器，作为 bearer 资源服务器过滤范围。 */
    @Bean
    public RequestMatcher businessResourceServerRequestMatcher(
            @Qualifier("businessRouteRequestMatcher") RequestMatcher businessRouteRequestMatcher,
            @Qualifier("internalLogoutRequestMatcher") RequestMatcher internalLogoutRequestMatcher) {
        return new OrRequestMatcher(List.of(businessRouteRequestMatcher, internalLogoutRequestMatcher));
    }

    /**
     * 返回 user/admin 业务路由的唯一目录。
     *
     * <p>文档元数据读取和短链兑换使用 {@code userAny}，表示读或写 scope 任一满足即可；
     * 创建、修改和正文写入相关入口仍明确要求 {@code document:write}。</p>
     */
    public static List<BusinessRouteAuthorizationEntry> entries() {
        return List.of(
                // user audio
                user("POST /user/audio/generate audio:write"), user("POST /user/audio/retry/{taskId} audio:write"),
                user("GET /user/audio/status/{taskId} audio:read"), user("POST /user/audio/list audio:read"),
                user("POST /user/audio/cancel/{taskId} audio:write"), user("DELETE /user/audio/{taskId} audio:write"),
                // user audit and media
                user("POST /user/audit/image/submitAudit audit:write"), user("POST /user/audit/image/cancelAudit audit:write"),
                user("POST /user/image/list media:read"), user("GET /user/image/overview media:read"),
                user("POST /user/image/upload media:write"), user("PUT /user/image/modify-file media:write"),
                user("PUT /user/image/modify-info media:write"), user("GET /user/image/{id} media:read"),
                user("DELETE /user/image/{id} media:write"),
                // user collaborative documents
                user("POST /user/document document:write"), user("GET /user/document document:read"),
                userAny("GET /user/document/{documentId}/meta document:read|document:write"),
                user("GET /user/document/{documentId}/users document:read"),
                user("PUT /user/document/{documentId}/users/{userId} document:write"),
                user("DELETE /user/document/{documentId}/users/{userId} document:write"),
                user("POST /user/document/{documentId}/share-links document:write"),
                user("GET /user/document/{documentId}/share-links document:read"),
                user("DELETE /user/document/{documentId}/share-links/{shareLinkId} document:write"),
                userAny("POST /user/document/share-links/{code}/redeem document:read|document:write"),
                userAny("GET /user/file/completion note:read|media:read|document:read"),
                user("PATCH /user/document/{documentId}/meta document:write"),
                user("DELETE /user/document/{documentId} document:write"),
                // user notes
                user("POST /user/note/list note:read"), user("GET /user/note/overview note:read"),
                user("POST /user/note/upload note:write"), user("PUT /user/note/upload/{noteId} note:write"),
                user("POST /user/note/upload/{noteId}/confirm note:write"), user("GET /user/note/upload/{noteId}/diff note:read"),
                user("PUT /user/note/publish/{noteId}/{status} note:write"), user("GET /user/note note:read"),
                user("GET /user/note/{noteId} note:read"), user("GET /user/note/source/{id} note:read"),
                user("GET /user/note/converted/{noteId} note:read"), user("POST /user/note/convert note:write"),
                user("DELETE /user/note/convert note:write"), user("PUT /user/note/{id}/info note:write"),
                user("DELETE /user/note/{id} note:write"), user("GET /user/note/search note:read"),
                user("POST /user/note/relation/check/{noteId} note:read"), user("GET /user/note/relation/{noteId} note:read"),
                user("GET /user/note/relation/images/{noteId} note:read+media:read"),
                user("GET /user/note/relation/backlinks/{noteId} note:read"),
                user("GET /user/note/relation/backlinks/tag/{tagId} note:read"),
                user("GET /user/note/relation/backlinks/image/{imageId} note:read+media:read"),
                user("PUT /user/note/relation/tag/bind note:write"), user("DELETE /user/note/relation/tag/unbind/{mappingId} note:write"),
                user("PUT /user/note/relation/image/bind note:write+media:read"), user("DELETE /user/note/relation/image/unbind/{mappingId} note:write"),
                user("PUT /user/note/relation/each/bind note:write"), user("DELETE /user/note/relation/each/unbind/{mappingId} note:write"),
                user("GET /user/public-note note:read"), user("GET /user/public-note/{noteId} note:read"),
                // user tag, audit and topic
                user("POST /user/tag/list note:read"), user("GET /user/tag/stats note:read"), user("GET /user/tag note:read"),
                user("POST /user/tag/add note:write"), user("POST /user/tag/batch-add note:write"), user("DELETE /user/tag/delete note:write"),
                user("POST /user/tag/assign note:write"), user("POST /user/tag/remove note:write"),
                user("POST /user/audit/note/submitAudit audit:write"), user("POST /user/audit/note/cancelAudit audit:write"),
                user("POST /user/audit/tag/submitAudit audit:write"), user("POST /user/audit/tag/cancelAudit audit:write"),
                user("POST /user/topic/list note:read"), user("GET /user/topic/children note:read"), user("GET /user/topic/stats note:read"),
                user("POST /user/topic/add note:write"), user("PUT /user/topic/modify note:write"), user("DELETE /user/topic/delete note:write"),
                // user account
                user("POST /user/email/resend-activation account:write"), user("GET /user/email/status account:read"),
                user("POST /user/email/change-code account:write"), user("POST /user/email/verify-change account:write"),
                user("GET /user/user/me account:read"), user("GET /user/user/overview account:read"),
                user("PUT /user/user/me account:write"), user("DELETE /user/user/me account:write"),
                // admin audio, audit and media
                admin("POST /admin/audio/list audio:read"), admin("GET /admin/audio/statistics audio:read"),
                admin("GET /admin/audio/{taskId} audio:read"), admin("POST /admin/audio/cancel/{taskId} audio:manage"),
                admin("DELETE /admin/audio/{taskId} audio:manage"), admin("POST /admin/audit/meta/list audit:read"),
                admin("POST /admin/audit/image/list audit:read"), admin("POST /admin/audit/note/list audit:read"),
                admin("PUT /admin/audit/meta/review/batch audit:manage"), admin("PUT /admin/audit/image/review/batch audit:manage"),
                admin("PUT /admin/audit/note/review/batch audit:manage"), admin("PUT /admin/image/audit/review audit:manage"),
                admin("PUT /admin/image/modify-info media:manage"), admin("PUT /admin/image/transfer-to-cloud media:manage"),
                admin("DELETE /admin/image/delete media:manage"), admin("POST /admin/image/list media:read"),
                admin("GET /admin/image/notes/{imageId} media:read+note:read"), admin("POST /admin/image/public/{isPublic} media:manage"),
                // admin notes and tags
                admin("GET /admin/note/source/{noteId} note:read"), admin("POST /admin/note/convert/{noteId} note:manage"),
                admin("DELETE /admin/note/convert/{noteId} note:manage"), admin("PUT /admin/note/force/{status}/{noteId} note:manage"),
                admin("DELETE /admin/note/delete note:manage"), admin("PUT /admin/note/info note:manage"),
                admin("POST /admin/note/list note:read"), admin("GET /admin/note/info/{noteId} note:read"),
                admin("GET /admin/note/open/{noteId} note:read"), admin("GET /admin/note/relation/backlinks/{noteId} note:read"),
                admin("GET /admin/note/relation/backlinks/tag/{tagId} note:read"),
                admin("GET /admin/note/relation/backlinks/image/{imageId} note:read+media:read"),
                admin("PUT /admin/tag/modify note:manage"), admin("DELETE /admin/tag/delete note:manage"), admin("POST /admin/tag/list note:read"),
                // admin topics and accounts
                admin("GET /admin/topic/{id} note:read"), admin("POST /admin/topic/list note:read"),
                admin("GET /admin/topic/children note:read"), admin("DELETE /admin/topic/delete note:manage"),
                admin("POST /admin/email/send account:manage"), admin("POST /admin/user/list account:read"),
                admin("PUT /admin/user/user account:manage"), admin("POST /admin/user/user account:manage"),
                admin("DELETE /admin/user/user account:manage"), admin("POST /admin/user/status/{status} account:manage"),
                admin("GET /admin/user/user account:read"), admin("GET /admin/user/me account:read"),
                // admin collaborative documents
                admin("GET /admin/document document:read"),
                admin("POST /admin/document/snapshot-history/clear document:manage")
        );
    }

    private static BusinessRouteAuthorizationEntry userAny(String specification) {
        // any-of 目录项允许同一资源同时服务 document:read 和 document:write 用户。
        return entry(specification, "user", true);
    }

    private static BusinessRouteAuthorizationEntry user(String specification) {
        // 普通 user 目录项要求 specification 中列出的全部 scope。
        return entry(specification, "user");
    }

    private static BusinessRouteAuthorizationEntry admin(String specification) {
        // admin 目录项只允许 admin client，并沿用 all-of scope 语义。
        return entry(specification, "admin");
    }

    private static BusinessRouteAuthorizationEntry entry(String specification, String clientId) {
        return entry(specification, clientId, false);
    }

    /** 解析“METHOD path scope”文本并构造已校验的授权目录项。 */
    private static BusinessRouteAuthorizationEntry entry(String specification, String clientId, boolean anyRequiredScope) {
        String[] fields = specification.split(" ", 3);
        if (fields.length != 3) {
            // 目录格式固定为方法、绝对路径和 scope 列表，字段缺失时启动应立即失败。
            throw new IllegalArgumentException("invalid route specification: " + specification);
        }
        String scopeSeparator = anyRequiredScope ? "\\|" : "\\+";
        // any-of 用 | 分隔 scope，all-of 用 + 分隔；解析后由 Entry 再校验 scope 合法性。
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList(fields[2].split(scopeSeparator)));
        return new BusinessRouteAuthorizationEntry(HttpMethod.valueOf(fields[0]), fields[1], scopes, clientId, anyRequiredScope);
    }
}
