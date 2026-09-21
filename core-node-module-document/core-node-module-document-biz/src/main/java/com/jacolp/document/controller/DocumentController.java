package com.jacolp.document.controller;

import com.jacolp.common.core.result.Result;
import com.jacolp.common.security.context.BaseContext;
import com.jacolp.document.api.model.DocumentMetadata;
import com.jacolp.document.application.authorization.DocumentUserAuthorizationService;
import com.jacolp.document.application.metadata.DocumentMetadataService;
import com.jacolp.document.application.share.DocumentShareLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供文档元数据 HTTP 接口；正文 CRDT 状态只通过 WebSocket 协作链路传输。 */
@RestController("User-DocumentController")
@RequestMapping("/user/document")
@Validated
@Schema(description = "User - 协作文档管理")
@Tag(name = "User-协作文档管理", description = "用户端协作文档元数据接口")
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentController {

    private final DocumentMetadataService metadataService;
    private final DocumentUserAuthorizationService authorizationService;
    private final DocumentShareLinkService shareLinkService;

    /** 创建文档元数据控制器，并保持正文协作由 WebSocket 端点负责。 */
    public DocumentController(DocumentMetadataService metadataService,
                               DocumentUserAuthorizationService authorizationService,
                               DocumentShareLinkService shareLinkService) {
        this.metadataService = Objects.requireNonNull(metadataService, "metadataService must not be null");
        this.authorizationService = Objects.requireNonNull(authorizationService,
                "authorizationService must not be null");
        this.shareLinkService = Objects.requireNonNull(shareLinkService, "shareLinkService must not be null");
    }

    /** 使用当前认证用户创建个人文档，不接受客户端提交的归属范围。 */
    @PostMapping
    @Operation(summary = "创建协作文档")
    public Result<DocumentMetadata> create(@RequestBody @Valid DocumentCreateRequest request) {
        return Result.success(metadataService.create(BaseContext.getCurrentId(), request.title()));
    }

    /** 返回当前认证用户可见的活跃文档元数据列表。 */
    @GetMapping
    @Operation(summary = "查询当前用户的协作文档列表")
    public Result<List<DocumentMetadata>> list() {
        return Result.success(metadataService.list(BaseContext.getCurrentId()));
    }

    /** 按当前用户范围读取一份文档元数据。 */
    @GetMapping("/{documentId}/meta")
    @Operation(summary = "查询协作文档元数据")
    public Result<DocumentMetadata> getMetadata(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId) {
        return Result.success(metadataService.get(BaseContext.getCurrentId(), documentId));
    }

    /** 仅文档所有者可以读取授权记录，已撤销记录也会返回。 */
    @GetMapping("/{documentId}/users")
    @Operation(summary = "查询协作文档授权名单")
    public Result<List<DocumentUserAuthorizationResponse>> listAuthorizations(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId) {
        return Result.success(authorizationService.list(BaseContext.getCurrentId(), documentId));
    }

    /** 仅文档所有者可以为启用用户设置 READ/WRITE 及 enabled 状态。 */
    @PutMapping("/{documentId}/users/{userId}")
    @Operation(summary = "新增或更新协作文档授权")
    public Result<DocumentUserAuthorizationResponse> upsertAuthorization(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId,
            @Parameter(description = "被授权用户 ID") @PathVariable @Positive long userId,
            @RequestBody @Valid DocumentUserAuthorizationRequest request) {
        return Result.success(authorizationService.upsert(BaseContext.getCurrentId(), documentId, userId,
                request.permission(), request.enabled()));
    }

    /** 仅将授权标记为 disabled，不物理删除授权历史。 */
    @DeleteMapping("/{documentId}/users/{userId}")
    @Operation(summary = "撤销协作文档授权")
    public Result<Void> revokeAuthorization(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId,
            @Parameter(description = "被授权用户 ID") @PathVariable @Positive long userId) {
        authorizationService.revoke(BaseContext.getCurrentId(), documentId, userId);
        return Result.success();
    }

    /** 由文档所有者创建带有效期、权限和最大兑换次数的短链。 */
    @PostMapping("/{documentId}/share-links")
    @Operation(summary = "创建协作文档分享短链")
    public Result<DocumentShareLinkResponse> createShareLink(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId,
            @RequestBody @Valid DocumentShareLinkRequest request) {
        return Result.success(shareLinkService.create(BaseContext.getCurrentId(), documentId,
                request.permission(), request.validForSeconds(), request.maxUses()));
    }

    /** 返回该文档的全部短链状态；列表不会重新暴露原始短链令牌。 */
    @GetMapping("/{documentId}/share-links")
    @Operation(summary = "查询协作文档分享短链")
    public Result<List<DocumentShareLinkResponse>> listShareLinks(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId) {
        return Result.success(shareLinkService.list(BaseContext.getCurrentId(), documentId));
    }

    /** 软撤销指定短链，保留历史记录和已产生的直接 ACL。 */
    @DeleteMapping("/{documentId}/share-links/{shareLinkId}")
    @Operation(summary = "取消协作文档分享短链")
    public Result<Void> revokeShareLink(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId,
            @Parameter(description = "分享短链 ID") @PathVariable @Positive long shareLinkId) {
        shareLinkService.revoke(BaseContext.getCurrentId(), documentId, shareLinkId);
        return Result.success();
    }

    /** 修改标题并返回更新后的元数据，正文内容不经过该接口。 */
    @PatchMapping("/{documentId}/meta")
    @Operation(summary = "修改协作文档标题")
    public Result<DocumentMetadata> updateMetadata(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId,
            @RequestBody @Valid DocumentMetadataUpdateRequest request) {
        return Result.success(metadataService.updateTitle(BaseContext.getCurrentId(), documentId, request.title()));
    }

    /** 仅在跨实例没有活跃 presence 时执行逻辑删除。 */
    @DeleteMapping("/{documentId}")
    @Operation(summary = "逻辑删除没有活跃协作会话的文档")
    public Result<Void> delete(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId) {
        metadataService.delete(BaseContext.getCurrentId(), documentId);
        return Result.success();
    }
}
