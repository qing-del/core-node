package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.jacolp.document.enums.DocumentPermission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code biz_document_share_link_redemption} 短链兑换记录的一行持久化模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentShareLinkRedemptionDO implements Serializable {

    /** Java 序列化版本号，不对应数据库字段。 */
    private static final long serialVersionUID = 1L;

    /** 短链记录 ID；与 userId 组成联合主键。 */
    private Long shareLinkId;
    /** 兑换短链的用户 ID；与 shareLinkId 组成联合主键。 */
    private Long userId;
    /** 本次兑换实际授予的权限。 */
    private DocumentPermission permission;
    /** 首次有效兑换时间。 */
    private LocalDateTime redeemedAt;
}
