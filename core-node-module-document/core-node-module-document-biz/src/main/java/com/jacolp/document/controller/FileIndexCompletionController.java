package com.jacolp.document.controller;

import com.jacolp.common.core.result.Result;
import com.jacolp.document.application.fileindex.FileCompletionItem;
import com.jacolp.document.application.fileindex.FileIndexCompletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供当前 token 范围内的文件名补全查询。 */
@RestController("User-FileIndexCompletionController")
@RequestMapping("/user/file")
@Validated
@Schema(description = "User - 文件补全")
@ConditionalOnProperty(prefix = "jacolp.document.file-index", name = "enabled", havingValue = "true")
public class FileIndexCompletionController {

    private final FileIndexCompletionService completionService;

    /** 创建文件补全控制器。 */
    public FileIndexCompletionController(FileIndexCompletionService completionService) {
        this.completionService = Objects.requireNonNull(completionService, "completionService must not be null");
    }

    /** 按 fileName.keyword 前缀返回当前用户有权访问的文件最小信息。 */
    @GetMapping("/completion")
    @Operation(summary = "文件名称补全")
    public Result<List<FileCompletionItem>> completion(
            @Parameter(description = "文件名此前缀") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "返回数量，范围 1-20")
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) Integer limit) {
        return Result.success(completionService.complete(keyword, limit));
    }
}
