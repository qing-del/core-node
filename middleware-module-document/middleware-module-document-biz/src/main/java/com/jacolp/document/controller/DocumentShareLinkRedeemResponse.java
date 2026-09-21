package com.jacolp.document.controller;

import com.jacolp.document.enums.DocumentPermission;
import java.util.Objects;

/** 短链兑换成功后的文档 ID、最终权限和所有者标记。 */
public record DocumentShareLinkRedeemResponse(long documentId, DocumentPermission permission, boolean owner) {
    /** 校验兑换结果的文档 ID 和权限，防止将不完整结果返回给客户端。 */
    public DocumentShareLinkRedeemResponse {
        if (documentId <= 0) {
            // 文档 ID 是前端后续元数据/WebSocket 请求的入口，非正数不能继续传播。
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(permission, "permission must not be null");
    }
}
