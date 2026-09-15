package com.jacolp.module.note.biz.application.controller.user;

import com.jacolp.module.note.biz.application.service.NoteCoreService;
import com.jacolp.module.note.biz.application.service.TagService;
import com.jacolp.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("User-NoteAuditApplicationController")
@RequestMapping("/user/audit")
@Slf4j
@CrossOrigin("*")
@Schema(description = "User - 审核管理")
@Tag(name = "User-审核管理", description = "用户端笔记、标签、图片审核申请入口")
public class UserNoteAuditApplicationController {

    @Autowired private NoteCoreService noteCoreService;
    @Autowired private TagService tagService;

    @PostMapping("/note/submitAudit")
    @Operation(summary = "发起笔记审核申请",
            description = "用户端集中审核入口，提交当前用户的笔记审核申请。")
    public Result<String> submitNoteAudit(@Parameter(description = "笔记ID") @RequestParam Long id) {
        log.info("User submit note audit via audit controller, noteId: {}", id);
        noteCoreService.submitNoteAudit(id);
        return Result.success("审核申请已提交");
    }

    @PostMapping("/note/cancelAudit")
    @Operation(summary = "撤销笔记审核申请",
            description = "用户端集中审核入口，撤销当前用户的笔记审核申请。")
    public Result<String> cancelNoteAudit(@Parameter(description = "笔记ID") @RequestParam Long id) {
        log.info("User cancel note audit via audit controller, noteId: {}", id);
        noteCoreService.cancelNoteAudit(id);
        return Result.success("审核申请已撤销");
    }

    @PostMapping("/tag/submitAudit")
    @Operation(summary = "发起标签审核申请",
            description = "用户端集中审核入口，提交当前用户的标签审核申请。")
    public Result<String> submitTagAudit(@Parameter(description = "标签ID") @RequestParam Long id) {
        log.info("User submit tag audit via audit controller, tagId: {}", id);
        tagService.submitTagAudit(id);
        return Result.success("审核申请已提交");
    }

    @PostMapping("/tag/cancelAudit")
    @Operation(summary = "撤销标签审核申请",
            description = "用户端集中审核入口，撤销当前用户的标签审核申请。")
    public Result<String> cancelTagAudit(@Parameter(description = "标签ID") @RequestParam Long id) {
        log.info("User cancel tag audit via audit controller, tagId: {}", id);
        tagService.cancelTagAudit(id);
        return Result.success("审核申请已撤销");
    }
}
