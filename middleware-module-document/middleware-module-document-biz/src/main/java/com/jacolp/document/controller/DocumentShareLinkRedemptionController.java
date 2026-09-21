package com.jacolp.document.controller;

import com.jacolp.common.core.result.Result;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.context.SecurityContextCurrentPrincipalAccessor;
import com.jacolp.document.application.share.DocumentShareLinkRedemptionService;
import java.net.URI;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Public navigation and authenticated redemption endpoints for document share links. */
@RestController
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentShareLinkRedemptionController {

    private final DocumentShareLinkRedemptionService redemptionService;
    private final String frontendBaseUrl;

    /** 初始化兑换服务和固定的前端分享页基础地址。 */
    public DocumentShareLinkRedemptionController(DocumentShareLinkRedemptionService redemptionService,
                                                 @Value("${jacolp.base-url}") String frontendBaseUrl) {
        this.redemptionService = Objects.requireNonNull(redemptionService);
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            // 没有目的地址时无法安全生成 Location 响应，宁可启动失败也不跳转到不确定位置。
            throw new IllegalStateException("jacolp.base-url is required");
        }
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    /**
     * 将短链导航到固定的前端分享路由。
     *
     * <p>该 GET 只负责导航，不查库、不兑换、不增加使用次数，因此使用临时 302 而不是可缓存的
     * 301；浏览器后续会在登录后显式调用 redeem 接口完成权限授予。</p>
     */
    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        HttpHeaders headers = new HttpHeaders();
        // 将 code 作为独立路径段编码，避免令牌中的特殊字符改变固定前端目的地址。
        URI destination = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .pathSegment("share", "documents", code)
                .build().encode().toUri();
        headers.setLocation(destination);
        // 短链包含凭证，禁止浏览器缓存和向第三方 Referer 泄露完整 URL。
        headers.setCacheControl(CacheControl.noStore());
        headers.set("Referrer-Policy", "no-referrer");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /** 读取当前已认证 user principal，并将兑换动作交给事务服务完成。 */
    @PostMapping("/user/document/share-links/{code}/redeem")
    public Result<DocumentShareLinkRedeemResponse> redeem(@PathVariable String code) {
        // 控制器不接受客户端传入 userId，身份只能来自安全上下文，避免冒用其他用户兑换。
        CurrentPrincipal principal = new SecurityContextCurrentPrincipalAccessor().currentPrincipal()
                .orElseThrow(() -> new com.jacolp.common.core.exception.AuthenticationException("当前登录信息已失效"));
        return Result.success(redemptionService.redeem(principal, code));
    }

    /** 去除配置基础地址的尾斜杠，避免拼接分享路由时出现重复斜杠。 */
    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        // 统一格式后，redirect 只需追加固定的 /share/documents/{code} 路径段。
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
