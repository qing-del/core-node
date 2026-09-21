package com.jacolp.document.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** 管理员批量清理协作文档历史快照的请求。 */
@Schema(description = "管理员协作文档历史快照清理请求")
public record AdminSnapshotHistoryClearRequest(
        @NotEmpty(message = "文档 ID 列表不能为空")
        @Schema(description = "待清理的文档 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull @Positive Long> documentIds) {
}
