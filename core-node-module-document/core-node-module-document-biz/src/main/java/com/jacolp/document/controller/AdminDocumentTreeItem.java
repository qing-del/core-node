package com.jacolp.document.controller;

import io.swagger.v3.oas.annotations.media.Schema;

/** 管理员文档树中的一个文档叶子；不暴露内部快照对象键。 */
@Schema(description = "管理员协作文档叶子节点")
public record AdminDocumentTreeItem(
        @Schema(description = "文档 ID") long documentId,
        @Schema(description = "文档标题") String title,
        @Schema(description = "最近修改时间（Unix 毫秒）") long lastModifyTime,
        @Schema(description = "最近修改用户 ID") Long lastModifyUserId,
        @Schema(description = "是否已有当前快照") boolean hasSnapshot) {
}
