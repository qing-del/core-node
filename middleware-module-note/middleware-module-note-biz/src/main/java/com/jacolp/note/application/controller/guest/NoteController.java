package com.jacolp.note.application.controller.guest;

import com.jacolp.common.core.result.PageResult;
import com.jacolp.common.core.result.Result;
import com.jacolp.note.application.dto.note.PublicNoteQueryDTO;
import com.jacolp.note.application.vo.note.PublicNoteDetailVO;
import com.jacolp.note.application.service.PublicNoteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("Guest-NoteController")
@RequestMapping("/guest/note")
@Slf4j
@Validated
@Schema(description = "Guest - 公开笔记")
@Tag(name = "Guest-公开笔记", description = "访客公开笔记只读接口")
public class NoteController {

    @Autowired private PublicNoteService publicNoteService;

    @GetMapping
    @Operation(summary = "分页查询公开笔记")
    public Result<PageResult> list(@Parameter(description = "公开笔记查询条件") PublicNoteQueryDTO dto) {
        log.info("Guest list published notes, topicId: {}, keyword: {}", dto.getTopicId(), dto.getKeyword());
        return Result.success(publicNoteService.listPublishedNotes(dto));
    }

    @GetMapping("/{noteId}")
    @Operation(summary = "查看公开笔记详情")
    public Result<PublicNoteDetailVO> detail(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Guest get published note detail, noteId: {}", noteId);
        return Result.success(publicNoteService.getPublishedNoteDetail(noteId));
    }
}
