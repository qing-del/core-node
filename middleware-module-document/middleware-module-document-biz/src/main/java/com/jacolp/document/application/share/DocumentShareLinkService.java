package com.jacolp.document.application.share;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.security.oauth2.token.OpaqueTokenProtector;
import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.controller.DocumentShareLinkResponse;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentShareLinkMapper;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理文档所有者创建、查询和取消的权限分享短链。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentShareLinkService {

    private static final long MAX_VALID_FOR_SECONDS = 365L * 24 * 60 * 60;
    private static final int MAX_USES = 100_000;

    private final DocumentAccessService accessService;
    private final DocumentShareLinkMapper shareLinkMapper;
    private final SecureOAuth2TokenGenerator tokenGenerator;
    private final OpaqueTokenProtector tokenProtector;
    private final String baseUrl;

    /** 初始化短链服务及令牌生成、摘要保护和外部 URL 配置。 */
    public DocumentShareLinkService(DocumentAccessService accessService,
                                    DocumentShareLinkMapper shareLinkMapper,
                                    SecureOAuth2TokenGenerator tokenGenerator,
                                    OpaqueTokenProtector tokenProtector,
                                    @Value("${jacolp.base-url}") String baseUrl) {
        this.accessService = Objects.requireNonNull(accessService, "accessService must not be null");
        this.shareLinkMapper = Objects.requireNonNull(shareLinkMapper, "shareLinkMapper must not be null");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
        this.tokenProtector = Objects.requireNonNull(tokenProtector, "tokenProtector must not be null");
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    /** 仅文档所有者可以创建短链；明文 token 不保存，只在响应 URL 中返回一次。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentShareLinkResponse create(long currentUserId, long documentId,
                                            DocumentPermission permission, long validForSeconds, int maxUses) {
        // 创建前先要求资源级 OWNER，调用方的全局 document:write scope 不能替代文档归属校验。
        accessService.requireOwner(documentId, currentUserId);
        validate(permission, validForSeconds, maxUses);

        // 原始 code 只存在于本次响应；数据库只保存不可逆摘要，泄露数据库不会直接泄露短链。
        String rawCode = tokenGenerator.newOpaqueToken();
        LocalDateTime now = LocalDateTime.now();
        DocumentShareLinkDO shareLink = new DocumentShareLinkDO(
                null, documentId, currentUserId, Base64.getUrlDecoder().decode(tokenProtector.fingerprint(rawCode)), permission,
                now.plusSeconds(validForSeconds), maxUses, 0, true, null, now, now);
        // Mapper 写入后回填自增主键，主键缺失表示短链记录未可靠落库，不能返回可用 URL。
        shareLinkMapper.insert(shareLink);
        if (shareLink.getId() == null || shareLink.getId() <= 0) {
            throw new BaseException("创建文档分享短链失败");
        }
        return toResponse(shareLink, baseUrl + "/s/" + rawCode);
    }

    /** 返回指定文档的全部短链，包括已取消、已过期及已耗尽记录。 */
    public List<DocumentShareLinkResponse> list(long currentUserId, long documentId) {
        accessService.requireOwner(documentId, currentUserId);
        List<DocumentShareLinkDO> links = shareLinkMapper.selectByDocumentId(documentId);
        if (links == null || links.isEmpty()) {
            // 空列表是正常业务结果；列表接口不返回原始 code，避免通过查询重新得到可兑换令牌。
            return List.of();
        }
        return links.stream().map(link -> toResponse(link, null)).toList();
    }

    /** 仅生成者可以取消短链；取消是软状态更新且可重复调用。 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(long currentUserId, long documentId, long shareLinkId) {
        accessService.requireOwner(documentId, currentUserId);
        if (shareLinkId <= 0) {
            // 路径参数会参与行锁查询，非正数不是有效短链 ID，应在进入数据库前拒绝。
            throw new IllegalArgumentException("shareLinkId must be positive");
        }
        // 锁定短链行，确保并发取消与兑换不会产生不一致的 enabled/used_count 状态。
        DocumentShareLinkDO link = shareLinkMapper.selectByIdForUpdate(shareLinkId);
        if (link == null || !Objects.equals(link.getDocumentId(), documentId)) {
            // 短链不存在或不属于当前文档时按幂等成功处理，避免泄露其他文档的短链存在性。
            return;
        }
        if (!Objects.equals(link.getCreatorUserId(), currentUserId)) {
            // 即使当前用户是文档所有者，也只能取消自己生成的短链。
            throw new BaseException("无权取消该文档分享短链");
        }
        // 软撤销保留历史记录和审计时间，不删除已产生的文档直接授权。
        shareLinkMapper.revokeByIdAndCreator(shareLinkId, currentUserId);
    }

    /** 校验分享权限、有效时长和最大兑换次数的业务边界。 */
    private static void validate(DocumentPermission permission, long validForSeconds, int maxUses) {
        if (permission == null || (permission != DocumentPermission.READ && permission != DocumentPermission.WRITE)) {
            // 枚举当前只有 READ/WRITE；未知值不能降级或默认成可编辑权限。
            throw new BaseException("分享权限必须为 READ 或 WRITE");
        }
        if (validForSeconds <= 0 || validForSeconds > MAX_VALID_FOR_SECONDS) {
            // 限制有效期既避免立即失效的无意义链接，也避免长期不受控的凭证。
            throw new BaseException("分享短链有效时长超出允许范围");
        }
        if (maxUses <= 0 || maxUses > MAX_USES) {
            // 至少一次且设置上限，防止错误参数导致无限制兑换。
            throw new BaseException("分享短链最大使用次数超出允许范围");
        }
    }

    /** 将持久化记录映射为接口响应；列表场景通过 null 隐藏原始短链 URL。 */
    private static DocumentShareLinkResponse toResponse(DocumentShareLinkDO link, String shareUrl) {
        if (link == null || link.getId() == null || link.getDocumentId() == null
                || link.getPermission() == null || link.getExpiresAt() == null
                || link.getMaxUses() == null || link.getUsedCount() == null) {
            // 关键字段缺失时不构造部分响应，避免客户端收到看似可用但无法兑换的链接。
            throw new BaseException("文档分享短链数据无效");
        }
        return new DocumentShareLinkResponse(link.getId(), link.getDocumentId(), link.getPermission(), shareUrl,
                link.getExpiresAt(), link.getMaxUses(), link.getUsedCount(), Boolean.TRUE.equals(link.getEnabled()),
                link.getRevokedAt(), link.getCreateTime(), link.getUpdateTime());
    }

    /** 去掉配置地址末尾斜杠，保证拼接 /s/{code} 时不会出现双斜杠。 */
    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            // 没有可信的基础地址就不能生成可用的分享 URL，启动时直接失败。
            throw new IllegalStateException("jacolp.base-url is required");
        }
        String normalized = value.trim();
        // 统一尾部格式，随后所有短链只需追加固定路径段。
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
