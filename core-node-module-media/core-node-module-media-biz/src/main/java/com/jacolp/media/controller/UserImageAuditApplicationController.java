package com.jacolp.media.controller;

import com.jacolp.media.application.service.MediaImageService;
import com.jacolp.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("User-ImageAuditApplicationController")
@RequestMapping("/user/audit")
@Slf4j
@Schema(description = "User - 审核管理")
@Tag(name = "User-审核管理", description = "用户端笔记、标签、图片审核申请入口")
public class UserImageAuditApplicationController {

    @Autowired private MediaImageService imageService;

    @PostMapping("/image/submitAudit")
    @Operation(summary = "发起图片审核申请",
            description = "用户端集中审核入口，提交当前用户的图片审核申请。")
    public Result<String> submitImageAudit(@Parameter(description = "图片ID") @RequestParam Long id) {
        log.info("User submit image audit via audit controller, imageId: {}", id);
        imageService.submitImageAudit(id);
        return Result.success("审核申请已提交");
    }

    @PostMapping("/image/cancelAudit")
    @Operation(summary = "撤销图片审核申请",
            description = "用户端集中审核入口，撤销当前用户的图片审核申请。")
    public Result<String> cancelImageAudit(@Parameter(description = "图片ID") @RequestParam Long id) {
        log.info("User cancel image audit via audit controller, imageId: {}", id);
        imageService.cancelImageAudit(id);
        return Result.success("审核申请已撤销");
    }
}
