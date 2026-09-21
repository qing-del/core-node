package com.jacolp.document.controller;

import com.jacolp.document.enums.DocumentPermission;
import java.time.LocalDateTime;
import java.util.Objects;

/** 对外返回的文档分享短链信息；原始令牌只会出现在创建响应的 shareUrl 中。 */
public record DocumentShareLinkResponse(
        /** 短链数据库主键。 */
        long shareLinkId,
        /** 被分享的文档数据库主键。 */
        long documentId,
        /** 短链兑换后授予的权限。 */
        DocumentPermission permission,
        /** 仅创建响应返回的原始短链 URL；查询列表时为空。 */
        String shareUrl,
        /** 短链失效时间。 */
        LocalDateTime expiresAt,
        /** 最大有效兑换次数。 */
        int maxUses,
        /** 已完成的有效兑换次数。 */
        int usedCount,
        /** 是否仍允许未来兑换。 */
        boolean enabled,
        /** 软撤销时间；未撤销时为空。 */
        LocalDateTime revokedAt,
        /** 记录创建时间。 */
        LocalDateTime createTime,
        /** 记录最近更新时间。 */
        LocalDateTime updateTime
) {
    /** 校验对外响应的主键、权限和有效期，避免返回不可用的短链记录。 */
    public DocumentShareLinkResponse {
        if (shareLinkId <= 0 || documentId <= 0) {
            // 主键会被后续撤销接口引用，非正数无法定位有效资源。
            throw new IllegalArgumentException("shareLinkId and documentId must be positive");
        }
        // permission/expiresAt 是客户端判断可兑换状态的核心字段，缺失时拒绝构造响应。
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
