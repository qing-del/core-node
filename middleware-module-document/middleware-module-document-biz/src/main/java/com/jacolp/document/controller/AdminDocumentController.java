package com.jacolp.document.controller;

import com.jacolp.common.core.result.Result;
import com.jacolp.document.application.admin.AdminDocumentService;
import com.jacolp.document.application.admin.AdminDocumentSnapshotHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员协作文档目录接口。 */
@RestController("Admin-DocumentController")
@RequestMapping("/admin/document")
@Schema(description = "Admin - 协作文档管理")
@Tag(name = "Admin-协作文档管理", description = "管理员协作文档目录与快照维护接口")
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class AdminDocumentController {

    private final AdminDocumentService adminDocumentService;
    private final AdminDocumentSnapshotHistoryService snapshotHistoryService;

    public AdminDocumentController(AdminDocumentService adminDocumentService,
                                   AdminDocumentSnapshotHistoryService snapshotHistoryService) {
        this.adminDocumentService = Objects.requireNonNull(adminDocumentService,
                "adminDocumentService must not be null");
        this.snapshotHistoryService = Objects.requireNonNull(snapshotHistoryService,
                "snapshotHistoryService must not be null");
    }

    /** 返回以用户为根节点的正常协作文档树。 */
    @GetMapping
    @Operation(summary = "查询协作文档目录", description = "按文档所有者分组返回全部正常协作文档，不返回 MinIO 对象键。")
    public Result<List<AdminDocumentTreeNode>> listTree() {
        return Result.success(adminDocumentService.listTree());
    }

    /** 清理所选正常文档的历史/孤儿快照，并返回已确认释放的 MinIO 空间。 */
    @PostMapping("/snapshot-history/clear")
    @Operation(summary = "批量清理协作文档历史快照",
            description = "保留每篇文档当前快照，仅删除未被当前指针引用的历史或孤儿快照，并返回实际释放字节数。")
    public Result<AdminSnapshotHistoryClearResponse> clearSnapshotHistory(
            @RequestBody @Valid AdminSnapshotHistoryClearRequest request) {
        return Result.success(snapshotHistoryService.clear(request.documentIds()));
    }
}
