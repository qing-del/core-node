package com.jacolp.document.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 以文档所有者为根节点的管理员协作文档树。 */
@Schema(description = "管理员协作文档用户根节点")
public record AdminDocumentTreeNode(
        @Schema(description = "用户 ID") long userId,
        @Schema(description = "登录用户名；历史用户资料缺失时为空") String username,
        @Schema(description = "用户昵称；历史用户资料缺失时为空") String nickname,
        @Schema(description = "该用户拥有的正常协作文档") List<AdminDocumentTreeItem> documents) {
}
