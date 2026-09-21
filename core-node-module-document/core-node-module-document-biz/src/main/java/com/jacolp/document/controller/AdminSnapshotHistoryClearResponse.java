package com.jacolp.document.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 管理员批量清理历史快照的汇总响应。 */
@Schema(description = "协作文档历史快照批量清理结果")
public record AdminSnapshotHistoryClearResponse(
        @Schema(description = "每篇文档的清理结果") List<AdminSnapshotHistoryClearItem> documents,
        @Schema(description = "全批次成功删除的对象数") long deletedObjectCount,
        @Schema(description = "全批次实际释放的 MinIO 字节数") long releasedBytes) {
}
