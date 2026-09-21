package com.jacolp.document.controller;

import io.swagger.v3.oas.annotations.media.Schema;

/** 一篇文档的历史快照清理结果；失败时已成功释放的空间仍会准确保留。 */
@Schema(description = "单篇协作文档历史快照清理结果")
public record AdminSnapshotHistoryClearItem(
        @Schema(description = "文档 ID") long documentId,
        @Schema(description = "成功删除的快照对象数") long deletedObjectCount,
        @Schema(description = "成功释放的 MinIO 字节数") long releasedBytes,
        @Schema(description = "该文档的清理是否完整成功") boolean success,
        @Schema(description = "失败提示；成功时为空") String failureMessage) {
}
