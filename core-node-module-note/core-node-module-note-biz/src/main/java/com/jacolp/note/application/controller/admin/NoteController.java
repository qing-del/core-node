package com.jacolp.note.application.controller.admin;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.common.core.result.Result;
import com.jacolp.common.core.utils.IdParserUtil;
import com.jacolp.note.application.dto.note.NoteModifyInfoDTO;
import com.jacolp.note.application.dto.note.NoteQueryDTO;
import com.jacolp.note.application.vo.note.NoteConvertResultVO;
import com.jacolp.note.application.vo.note.NoteDetailVO;
import com.jacolp.note.application.service.NoteContextService;
import com.jacolp.note.application.service.NoteConvertService;
import com.jacolp.note.application.service.NoteCoreService;
import com.jacolp.note.application.facade.NoteFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("Admin-NoteController")
@RequestMapping("/admin/note")
@Slf4j
@Validated
@Schema(description = "Admin - 笔记管理")
@Tag(name = "Admin-笔记管理", description = "笔记生命周期管理接口")
public class NoteController {

    @Autowired private NoteFacade noteFacade;
    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteContextService noteContextService;
    @Autowired private NoteConvertService noteConvertService;

    @GetMapping(value = "/source/{noteId}")
    @Operation(summary = "获取笔记 Markdown 源内容",
            description = "直接读取数据库中的笔记原文并以纯文本形式返回，供编辑器回显或二次编辑。")
    public Result<String> getSource(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin get note source, noteId: {}", noteId);
        return Result.success(noteContextService.adminGetSource(noteId));
    }

    @PostMapping("/convert/{noteId}")
    @Operation(summary = "转换笔记为 HTML",
            description = "转换前先校验笔记不存在缺失关联信息，然后调用 MarkdownHtmlEngine 生成前置元信息、TOC 和正文 HTML，并将结果写入转换缓存表，供前端阅读页直接渲染。")
    public Result convert(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin convert note, noteId: {}", noteId);
        noteFacade.convertNote(noteId);
        return Result.success();
    }

    @DeleteMapping("/convert/{noteId}")
    @Operation(summary = "删除笔记转换缓存",
            description = "删除指定笔记的转换缓存记录，同时将发布状态重置为待转换，避免前端继续读取失效内容。")
    public Result deleteConverted(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin delete note converted, noteId: {}", noteId);
        noteConvertService.delete(noteId);
        noteCoreService.adminDeleteConverted(noteId);
        return Result.success();
    }

    @PutMapping("/force/{status}/{noteId}")
    @Operation(summary = "强制设置笔记状态（暂不支持）",
            description = "预留接口，当前暂不支持强制设置笔记状态，调用时将抛出异常。")
    public Result forceSetNoteStatus(
            @Parameter(description = "存在的笔记状态之一即可") @PathVariable Long status,
            @Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin force set note status, noteId: {}, status: {}", noteId, status);

        throw new BaseException("暂不支持强制设置笔记状态");    // TODO 后续开发
    }


    @DeleteMapping("/delete")
    @Operation(summary = "批量删除笔记",
            description = "批量删除笔记主记录并同步清理转换结果、Diff、内容和三类关联映射，随后回收当前用户已占用的存储空间。")
    public Result<String> delete(@Parameter(description = "笔记ID，使用英文逗号分隔")
                                 @RequestParam @NotBlank(message = "待删除的笔记 ID 列表不能为空") String ids) {
        List<Long> idList = IdParserUtil.parseIds(ids, "笔记");
        log.info("Admin delete notes, ids: {}", idList);

        noteFacade.adminDeleteNotes(idList);
        return Result.success();
    }

    @PutMapping("/info")
    @Operation(summary = "修改笔记元信息",
            description = "修改笔记描述和主题等基础元数据，不变更标题和 Markdown 正文；修改前会校验目标主题有效性和同主题同名唯一性。")
    public Result<String> modifyInfo(
            @Parameter(description = "笔记元信息修改请求（笔记ID、新描述、新主题ID）") @RequestBody NoteModifyInfoDTO dto) {
        log.info("Admin modify note info, noteId: {}", dto.getId());
        noteCoreService.modifyInfo(dto);
        return Result.success();
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询笔记",
            description = "按用户、主题、标题、发布状态、审核状态和缺失状态等条件分页查询笔记列表，并按创建时间倒序返回。")
    public Result<PageResult> list(
            @Parameter(description = "笔记查询条件（用户ID、主题ID、标题关键词、发布状态、审核状态）") @RequestBody NoteQueryDTO dto) {
        log.info("Admin list notes, dto:{}", dto);
        return Result.success(noteCoreService.listNotes(dto));
    }

    @GetMapping("/info/{noteId}")
    @Operation(summary = "查询笔记详情",
            description = "返回笔记基础元数据，并聚合标签、图片、双链映射及已转换内容，供前端详情页一次性加载。")
    public Result<NoteDetailVO> info(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin get note info, noteId: {}", noteId);
        return Result.success(noteFacade.getInfo(noteId));
    }

    @GetMapping("/open/{noteId}")
    @Operation(summary = "打开笔记内容",
            description = "读取指定笔记的已转换结果（TOC + 正文 HTML），管理端用作查看他人笔记的接口，用于前端阅读页渲染。")
    public Result<NoteConvertResultVO> open(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin open note, noteId: {}", noteId);
        return Result.success(noteConvertService.getNoteConvert(noteId));
    }
}
