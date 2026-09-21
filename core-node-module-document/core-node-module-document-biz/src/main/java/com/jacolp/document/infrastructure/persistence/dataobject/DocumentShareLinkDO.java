package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.jacolp.document.enums.DocumentPermission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code biz_document_share_link} 文档分享短链的一行持久化模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentShareLinkDO implements Serializable {

    /** Java 序列化版本号，不对应数据库字段。 */
    private static final long serialVersionUID = 1L;

    /** 短链记录 ID。 */
    private Long id;
    /** 被分享的文档 ID。 */
    private Long documentId;
    /** 短链生成者用户 ID。 */
    private Long creatorUserId;
    /** 原始短链令牌的 SHA-256 摘要，不保存原始令牌。 */
    private byte[] tokenHash;
    /** 兑换后授予的文档权限；WRITE 隐含 READ。 */
    private DocumentPermission permission;
    /** 短链允许兑换到的时间。 */
    private LocalDateTime expiresAt;
    /** 最大有效兑换次数。 */
    private Integer maxUses;
    /** 已完成的有效兑换次数。 */
    private Integer usedCount;
    /** 短链是否仍可兑换；false 表示已取消。 */
    private Boolean enabled;
    /** 短链取消时间。 */
    private LocalDateTime revokedAt;
    /** 短链记录创建时间。 */
    private LocalDateTime createTime;
    /** 短链记录最近更新时间。 */
    private LocalDateTime updateTime;
}
