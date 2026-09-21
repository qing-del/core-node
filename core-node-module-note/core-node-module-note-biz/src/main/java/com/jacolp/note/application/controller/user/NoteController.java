package com.jacolp.note.application.controller.user;

import com.jacolp.note.infrastructure.persistence.dataobject.NoteContextDO;
import com.jacolp.note.infrastructure.web.NoteFileLimit;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.common.core.result.Result;
import com.jacolp.constant.NoteConstant;
import com.jacolp.note.application.dto.note.NoteChangeConfirmDTO;
import com.jacolp.note.application.dto.note.NoteModifyInfoDTO;
import com.jacolp.note.application.dto.note.UserNoteQueryDTO;
import com.jacolp.note.application.dto.note.UserNoteSearchDTO;
import com.jacolp.note.application.vo.note.NoteStatsVO;
import com.jacolp.note.application.vo.note.NoteConvertResultVO;
import com.jacolp.note.application.vo.note.NoteDetailVO;
import com.jacolp.note.application.vo.note.NoteDiffVO;
import com.jacolp.note.application.vo.note.NoteModifyDiffDetailVO;
import com.jacolp.note.application.vo.note.NoteUploadVO;
import com.jacolp.note.application.service.NoteConvertService;
import com.jacolp.note.application.service.NoteContextService;
import com.jacolp.note.application.service.NoteCoreService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.note.application.facade.NoteFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户端笔记控制器
 */
@RestController("User-NoteController")
@RequestMapping("/user/note")
@Slf4j
@Validated
@Schema(description = "User - 笔记管理")
@io.swagger.v3.oas.annotations.tags.Tag(name = "User-笔记管理", description = "用户端笔记管理接口")
public class NoteController {

    @Autowired private NoteFacade noteFacade;
    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteContextService noteContextService;
    @Autowired private NoteConvertService noteConvertService;

    @PostMapping("/list")
    @Operation(summary = "条件查询笔记列表")
    public Result<PageResult> list(
            @Parameter(description = "笔记查询条件（主题ID、状态、分页参数）") @RequestBody UserNoteQueryDTO dto) {
        log.info("User list notes, topicId: {}",dto.getTopicId());
        return Result.success(noteCoreService.listUserNotes(dto));
    }

    @GetMapping("/overview")
    @Operation(summary = "获取用户笔记统计")
    public Result<NoteStatsVO> getOverview() {
        log.info("User get note overview");
        return Result.success(noteCoreService.getUserNoteOverview());
    }

    @PostMapping("/upload")
    @NoteFileLimit
    @Operation(summary = "上传笔记",
            description = "从当前登录用户上下文获取 userId 后上传 Markdown 文件，先校验主题是否存在与同主题同名唯一性，再一次性扫描标签、图片和双链引用并建立映射；同时落库笔记原文、初始化缺失状态，最终返回 noteId 与缺失图片列表。")
    public Result<NoteUploadVO> upload(
            @Parameter(description = "所属主题ID（可选）") @RequestParam(required = false) Long topicId,
            @Parameter(description = "笔记Markdown文件") @RequestParam MultipartFile file) {
        log.info("User upload note, topicId: {}, filename: {}", topicId, file.getOriginalFilename());
        return Result.success(noteFacade.uploadNote(file, topicId));
    }

    @PutMapping("/upload/{noteId}")
    @NoteFileLimit
    @Operation(summary = "修改笔记源文件",
            description = "校验笔记归属后读取旧 Markdown 内容，与新文件一起重新扫描标签、图片和双链引用并计算 Diff；新内容仅写入临时版本和变更记录，等待后续确认或回滚。")
    public Result<NoteDiffVO> modifySource(
            @Parameter(description = "笔记ID") @PathVariable Long noteId,
            @Parameter(description = "新笔记文件") @RequestParam MultipartFile file) {
        log.info("User modify note source, noteId: {}, filename: {}", noteId, file.getOriginalFilename());
        return Result.success(noteFacade.modifyNoteSource(noteId, file));
    }

    @PostMapping("/upload/{noteId}/confirm")
    @Operation(summary = "确认或取消笔记变更",
            description = "对 modify-upload 产生的待确认 Diff 进行最终处理：确认时用新内容覆盖旧内容并重建关联映射，取消时清理临时内容和变更记录；整个过程保持笔记原有发布状态不变。")
    public Result confirmChange(
            @Parameter(description = "笔记ID") @PathVariable Long noteId,
            @Parameter(description = "变更确认请求（包含确认或取消标记）") @RequestBody @Valid @NotNull NoteChangeConfirmDTO dto) {
        log.info("User confirm note change, noteId: {}", noteId);
        noteFacade.confirmChange(noteId, dto);
        return Result.success();
    }

    @GetMapping("/upload/{noteId}/diff")
    @Operation(summary = "查询变更 Diff 详情",
            description = "读取指定笔记的旧内容、新内容和 diff 记录，返回给前端用于变更确认页面展示；若没有待确认内容则按业务规则返回不存在。")
    public Result<NoteModifyDiffDetailVO> getModifyDiff(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("User get note modify diff, noteId: {}", noteId);
        return Result.success(noteFacade.getModifyDiff(noteId));
    }

    @PutMapping("/publish/{noteId}/{status}")
    @Operation(summary = "设置笔记发布状态",
            description = "根据 status 设置笔记发布或下架；发布时必须已存在转换缓存，并且标签、图片、双链三类关联都已通过审核，以及笔记本身通过审核，否则拒绝发布。用户端调用的时候会校验笔记是否具有所属权！")
    public Result<String> setPublishStatus(
            @Parameter(description = "笔记ID") @PathVariable Long noteId,
            @Parameter(description = "发布状态（1:发布, 0:下架）") @PathVariable Short status) {
        log.info("User set note publish status, noteId: {}, status: {}", noteId, status);
        status = status == 1 ? NoteConstant.STATUS_PUBLISHED : NoteConstant.STATUS_APPROVED;
        noteFacade.updateNoteStatus(noteId, status);
        return Result.success();
    }

    @GetMapping
    @Operation(summary = "查询当前用户笔记列表")
    public Result<PageResult> listMyNotes(
            @Parameter(description = "当前用户笔记搜索条件（主题ID、关键词、分页参数）") UserNoteSearchDTO dto) {
        log.info("User list notes, topicId: {}, keyword: {}", dto.getTopicId(), dto.getKeyword());
        return Result.success(noteCoreService.listUserNotesBySearch(dto));
    }

    @GetMapping("/{noteId}")
    @Operation(summary = "查看笔记详情")
    public Result<NoteDetailVO> getDetail(
            @Parameter(description = "笔记ID")
            @PathVariable Long noteId) {
        log.info("User get note detail: {}", noteId);
        return Result.success(noteFacade.getInfo(noteId));
    }

    @GetMapping(value = "/source/{id}")
    @Operation(summary = "获取笔记Markdown源内容")
    public Result<String> getSource(
            @Parameter(description = "笔记ID")
            @PathVariable Long id) {
        log.info("User get note source: {}", id);
        NoteContextDO context = noteContextService.getByNoteIdWithValid(id);
        return Result.success(context != null ? context.getMarkdownContent() : "");
    }

    @GetMapping("/converted/{noteId}")
    @Operation(summary = "获取笔记转换后的HTML内容",
            description = "获取笔记转换后的 HTML 内容，但是一般用于获取自己的笔记形式，不能用来查询公开的笔记内容。")
    public Result<NoteConvertResultVO> getConvertedHtml(
            @Parameter(description = "笔记ID")
            @PathVariable Long noteId) {
        log.info("User get note converted HTML: {}", noteId);
        return Result.success(noteConvertService.getNoteConvert(noteId));
    }


    @PostMapping("/convert")
    @Operation(summary = "转换笔记",
        description = "将笔记的 Markdown 源文件转换成 HTML 文件，并保存到数据库中。")
    public Result convert(@Parameter(description = "笔记ID") @RequestParam Long noteId) {
        log.info("User convert note: {}", noteId);
        noteFacade.convertNote(noteId);
        return Result.success();
    }

    @DeleteMapping("/convert")
    @Operation(summary = "删除笔记转换缓存",
           description = "删除笔记转换缓存，并清理临时文件和转换记录；同时将笔记状态转换为“待转换”。")
    public Result deleteConverted(@Parameter(description = "笔记ID") @RequestParam Long noteId) {
        log.info("User delete converted note cache, noteId: {}", noteId);
        noteFacade.deleteConverted(noteId);
        return Result.success();
    }

    @PutMapping("/{id}/info")
    @Operation(summary = "修改笔记元信息")
    public Result updateInfo(
            @Parameter(description = "笔记ID") @PathVariable Long id,
            @Parameter(description = "笔记元信息修改请求（描述、主题ID等）") @RequestBody NoteModifyInfoDTO dto) {
        log.info("User update note info: {}", id);
        noteCoreService.modifyInfo(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除笔记")
    public Result delete(
            @Parameter(description = "笔记ID")
            @PathVariable Long id) {
        log.info("User delete note: {}", id);
        noteFacade.deleteNote(id);
        return Result.success();
    }

    @GetMapping("/search")
    @Operation(summary = "全文搜索笔记")
    public Result<PageResult> search(
            @Parameter(description = "全文搜索条件（关键词、主题ID、分页参数）") UserNoteSearchDTO dto) {
        log.info("User search notes, keyword: {}", dto.getKeyword());
        return Result.success(noteCoreService.searchUserNotes(dto));
    }
}
