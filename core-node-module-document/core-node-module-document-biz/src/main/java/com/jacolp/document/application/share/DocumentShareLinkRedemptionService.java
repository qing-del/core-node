package com.jacolp.document.application.share;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.oauth2.authorization.PermissionScopeMatcher;
import com.jacolp.document.application.access.DocumentAccessDeniedException;
import com.jacolp.document.controller.DocumentShareLinkRedeemResponse;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkRedemptionDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentShareLinkMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import com.jacolp.system.api.UserProfileApi;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jacolp.common.security.oauth2.token.OpaqueTokenProtector;

/** 将文档分享短链兑换为文档级直接 ACL，并保证兑换幂等、限次和并发安全。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentShareLinkRedemptionService {

    private final DocumentShareLinkMapper shareLinkMapper;
    private final DocumentMapper documentMapper;
    private final DocumentUserMappingMapper mappingMapper;
    private final UserProfileApi userProfileApi;
    private final OpaqueTokenProtector tokenProtector;

    /** 初始化短链、文档、ACL 和用户状态的持久化依赖。 */
    public DocumentShareLinkRedemptionService(DocumentShareLinkMapper shareLinkMapper,
                                              DocumentMapper documentMapper,
                                              DocumentUserMappingMapper mappingMapper,
                                              UserProfileApi userProfileApi,
                                              OpaqueTokenProtector tokenProtector) {
        this.shareLinkMapper = Objects.requireNonNull(shareLinkMapper);
        this.documentMapper = Objects.requireNonNull(documentMapper);
        this.mappingMapper = Objects.requireNonNull(mappingMapper);
        this.userProfileApi = Objects.requireNonNull(userProfileApi);
        this.tokenProtector = Objects.requireNonNull(tokenProtector);
    }

    /**
     * 在一个事务中兑换短链。
     *
     * <p>先锁定短链行并校验有效期/配额，再检查文档、用户和全局 scope；成功后写入直接 ACL、
     * 用户兑换台账并递增使用次数。联合主键使同一用户重复提交保持幂等。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentShareLinkRedeemResponse redeem(CurrentPrincipal principal, String code) {
        if (principal == null || !"user".equals(principal.clientId())) {
            // 短链只能由已认证的 user client 兑换，admin 或匿名请求不能借此获得文档权限。
            throw DocumentAccessDeniedException.forbidden();
        }
        if (code == null || code.isBlank() || code.length() > 256) {
            // 限制 code 长度和空值，既避免无效查询也防止异常输入进入摘要解码流程。
            throw DocumentAccessDeniedException.forbidden();
        }

        byte[] tokenHash;
        try {
            // 只对客户端提交的 code 做 SHA-256 摘要；数据库永远不保存原始短链令牌。
            tokenHash = Base64.getUrlDecoder().decode(tokenProtector.fingerprint(code));
        } catch (IllegalArgumentException exception) {
            // 非法 Base64/令牌格式统一返回中性拒绝，不暴露摘要实现细节。
            throw DocumentAccessDeniedException.forbidden();
        }
        // 先用唯一摘要定位候选记录，再按主键加行锁，串行化同一短链的配额竞争。
        DocumentShareLinkDO candidate = shareLinkMapper.selectByTokenHash(tokenHash);
        if (candidate == null || candidate.getId() == null) {
            // 不存在的摘要与已知短链使用同一错误，避免通过响应探测有效 code。
            throw DocumentAccessDeniedException.forbidden();
        }
        DocumentShareLinkDO link = shareLinkMapper.selectByIdForUpdate(candidate.getId());
        if (link == null || !validLink(link)) {
            // 行锁读取后再次校验 enabled、过期时间和剩余次数，防止取消/耗尽链接继续兑换。
            throw DocumentAccessDeniedException.forbidden();
        }
        requireScope(principal, link.getPermission());

        // 只允许兑换到活跃文档和启用账号；文档删除及账号停用都不产生 ACL 副作用。
        DocumentDO document = documentMapper.selectActiveById(link.getDocumentId());
        if (document == null || document.getOwnerUserId() == null || !userProfileApi.isActiveUser(principal.userId())) {
            throw DocumentAccessDeniedException.forbidden();
        }
        if (Objects.equals(document.getOwnerUserId(), principal.userId())) {
            // 所有者本来就拥有 WRITE，不写入自授权记录，也不消耗短链次数。
            return new DocumentShareLinkRedeemResponse(document.getId(), DocumentPermission.WRITE, true);
        }

        // 同时读取兑换台账和当前 ACL：台账负责幂等，ACL 负责反映撤销/降级后的真实权限。
        DocumentShareLinkRedemptionDO existingRedemption = shareLinkMapper.selectRedemption(link.getId(), principal.userId());
        DocumentUserMappingDO mapping = mappingMapper.selectByDocumentIdAndUserId(link.getDocumentId(), principal.userId());
        if (existingRedemption != null) {
            // 台账只负责保证兑换幂等；当前仍 enabled 的 ACL 才是权限来源，不能靠台账复活已撤销映射。
            DocumentPermission currentPermission = effectiveMappingPermission(mapping);
            if (currentPermission == null) {
                // 台账存在但 ACL 已被撤销/禁用时拒绝访问，不能借幂等记录重新复活权限。
                throw DocumentAccessDeniedException.forbidden();
            }
            // 重复兑换不重复写 ACL、不增加 used_count，只返回当前仍生效的权限。
            return new DocumentShareLinkRedeemResponse(document.getId(), currentPermission, false);
        }
        // 新兑换取现有启用 ACL 与短链权限的最高级别，READ 绝不覆盖已有 WRITE。
        DocumentPermission finalPermission = highest(effectiveMappingPermission(mapping), link.getPermission());
        boolean alreadyWritable = mapping != null
                && Boolean.TRUE.equals(mapping.getEnabled())
                && mapping.getPermission() == DocumentPermission.WRITE;
        if (!alreadyWritable) {
            // 没有 WRITE 时写入新增/升级后的直接 ACL；disabled 记录可以在此被重新启用。
            DocumentUserMappingDO grant = new DocumentUserMappingDO(link.getDocumentId(), principal.userId(),
                    finalPermission, true, null, null);
            if (mappingMapper.upsertByDocumentOwner(grant, document.getOwnerUserId()) <= 0) {
                // owner/deleted 条件未命中时回滚事务，确保不会只留下兑换次数或台账。
                throw DocumentAccessDeniedException.forbidden();
            }
        }
        // ACL 成功后记录本次兑换；联合主键和原子计数共同保证限次语义。
        DocumentShareLinkRedemptionDO redemption = new DocumentShareLinkRedemptionDO(link.getId(), principal.userId(),
                link.getPermission(), LocalDateTime.now());
        if (shareLinkMapper.insertRedemption(redemption) <= 0 || shareLinkMapper.incrementUsedCountIfAvailable(link.getId()) <= 0) {
            // 任一步骤失败都抛出异常，让事务回滚此前的 ACL 写入，避免部分成功状态。
            throw DocumentAccessDeniedException.forbidden();
        }
        return new DocumentShareLinkRedeemResponse(document.getId(), finalPermission, false);
    }

    /** 判断短链是否处于启用、未过期、未耗尽且字段完整的可兑换状态。 */
    private static boolean validLink(DocumentShareLinkDO link) {
        return Boolean.TRUE.equals(link.getEnabled()) && link.getExpiresAt() != null
                && link.getExpiresAt().isAfter(LocalDateTime.now())
                && link.getMaxUses() != null && link.getUsedCount() != null
                && link.getUsedCount() < link.getMaxUses()
                && link.getDocumentId() != null && link.getPermission() != null;
    }

    /** 判断短链是否还有可用兑换次数；供配额语义复用。 */
    private static boolean hasQuota(DocumentShareLinkDO link) {
        return link.getUsedCount() < link.getMaxUses();
    }

    /** 检查兑换者的全局文档 scope，短链权限不能提升 OAuth scope。 */
    private static void requireScope(CurrentPrincipal principal, DocumentPermission permission) {
        String required = permission == DocumentPermission.WRITE ? "document:write" : "document:read";
        if (permission == DocumentPermission.WRITE
                ? !PermissionScopeMatcher.grants(principal.scopes(), required)
                : !(PermissionScopeMatcher.grants(principal.scopes(), "document:read")
                || PermissionScopeMatcher.grants(principal.scopes(), "document:write"))) {
            // WRITE 必须具备全局写 scope；READ 可由读或写 scope 满足，均不改变资源级 ACL 结果。
            throw new com.jacolp.common.core.exception.PermissionDeniedException("权限不足");
        }
    }

    /** 返回两个权限中的最高级别，允许 WRITE 升级 READ 但不发生降级。 */
    private static DocumentPermission highest(DocumentPermission first, DocumentPermission second) {
        return first == DocumentPermission.WRITE || second == DocumentPermission.WRITE
                ? DocumentPermission.WRITE : DocumentPermission.READ;
    }

    /** 只把 enabled 且权限字段完整的映射视为当前有效 ACL。 */
    private static DocumentPermission effectiveMappingPermission(DocumentUserMappingDO mapping) {
        return mapping != null && Boolean.TRUE.equals(mapping.getEnabled()) ? mapping.getPermission() : null;
    }
}
