package com.jacolp.note.application.controller.user;

import java.util.List;

import com.jacolp.common.core.result.Result;
import com.jacolp.note.application.dto.tag.TagMappingBindDTO;
import com.jacolp.note.application.vo.note.ImageBacklinkVO;
import com.jacolp.note.application.vo.note.NoteBacklinkVO;
import com.jacolp.note.application.vo.note.NoteCheckBindingVO;
import com.jacolp.note.application.vo.note.TagBacklinkVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jacolp.note.application.facade.NoteRelationFacade;
import com.jacolp.note.application.dto.image.ImageMappingBindDTO;
import com.jacolp.note.application.dto.note.EachMappingBindDTO;
import com.jacolp.note.application.vo.image.ImageSimpleVO;
import com.jacolp.note.application.vo.note.NoteRelationDetailVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;

@RestController("User-NoteRelationController")
@RequestMapping("/user/note/relation")
@Schema(description = "User - 笔记关联管理")
@io.swagger.v3.oas.annotations.tags.Tag(name = "User-笔记关联管理", description = "用户端笔记关联管理接口")
@Slf4j
public class NoteRelationController {

    @Autowired private NoteRelationFacade noteRelationFacade;

    @PostMapping("/check/{noteId}")
    @Operation(summary = "校验关联完整性",
            description = "遍历笔记的标签、图片和双链三类映射，判断是否都已完整绑定且审核通过，会自动转换笔记状态；如果收到的结果中`isCompeted`这个值不为true即为缺失信息转换失败。")
    public Result<NoteCheckBindingVO> checkRelationCompletion(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin check note relation completion, noteId: {}", noteId);
        return Result.success(noteRelationFacade.checkRelationCompletion(noteId));
    }

    @GetMapping("/{noteId}")
    @Operation(summary = "查询笔记关联映射",
            description = "查询笔记与标签、图片、双链笔记三类关联的全部映射行、绑定状态和缺失标记，用于编辑器联动展示。")
    public Result<NoteRelationDetailVO> relationInfo(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("User get note relation info, noteId: {}", noteId);
        return Result.success(noteRelationFacade.getRelationInfo(noteId));
    }

    @GetMapping("/images/{noteId}")
    @Operation(summary = "查询笔记关联图片",
            description = "按笔记 ID 读取图片映射及图片基础信息，返回给前端用于绑定状态展示、差异确认和详情页渲染。")
    public Result<List<ImageSimpleVO>> listImages(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin list note images, noteId: {}", noteId);
        return Result.success(noteRelationFacade.listImageSimpleVOsByNoteId(noteId));
    }

    @GetMapping("/backlinks/{noteId}")
    @Operation(summary = "查询反向引用笔记",
            description = "按笔记 ID 反查所有引用了它的源笔记。仅返回当前用户拥有，或源笔记已公开(status=6)的引用方；目标笔记本身需为当前用户拥有或已公开。")
    public Result<List<NoteBacklinkVO>> listBacklinks(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("User list note backlinks, noteId: {}", noteId);
        return Result.success(noteRelationFacade.listBacklinksByNoteId(noteId));
    }

    @GetMapping("/backlinks/tag/{tagId}")
    @Operation(summary = "查询标签反向引用笔记",
            description = "按标签 ID 反查所有引用了它的源笔记。目标标签需为当前用户拥有或已通过审核(auditStatus=2)。")
    public Result<List<TagBacklinkVO>> listTagBacklinks(@Parameter(description = "标签ID") @PathVariable Long tagId) {
        log.info("User list tag backlinks, tagId: {}", tagId);
        return Result.success(noteRelationFacade.listBacklinksByTagId(tagId));
    }

    @GetMapping("/backlinks/image/{imageId}")
    @Operation(summary = "查询图片反向引用笔记",
            description = "按图片 ID 反查所有引用了它的源笔记。目标图片需为当前用户拥有或已公开(isPublic=1)。")
    public Result<List<ImageBacklinkVO>> listImageBacklinks(@Parameter(description = "图片ID") @PathVariable Long imageId) {
        log.info("User list image backlinks, imageId: {}", imageId);
        return Result.success(noteRelationFacade.listBacklinksByImageId(imageId));
    }

    @PutMapping("/tag/bind")
    @Operation(summary = "绑定标签映射",
            description = "为指定标签映射行绑定目标标签，绑定前会校验名称一致性与标签审核状态，成功后刷新笔记缺失信息。")
    public Result<String> bindTag(
            @Parameter(description = "标签绑定请求（映射行ID、目标标签ID）") @RequestBody TagMappingBindDTO dto) {
        log.info("Admin bind tag mapping, mappingId: {}, tagId: {}", dto.getMappingId(), dto.getTagId());
        noteRelationFacade.bindTagMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/tag/unbind/{mappingId}")
    @Operation(summary = "解绑标签映射",
            description = "解除指定标签映射行与标签的绑定关系，清空 tagId 与审核标记后重新计算笔记缺失信息。")
    public Result<String> unbindTag(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind tag mapping, mappingId: {}", mappingId);
                noteRelationFacade.unbindTagMapping(mappingId);
        return Result.success();
    }

    @PutMapping("/image/bind")
    @Operation(summary = "绑定图片映射",
            description = "为指定图片映射行绑定目标图片，绑定前会校验文件名一致性和图片审核状态，同时计算是否跨用户引用。")
    public Result<String> bindImage(
            @Parameter(description = "图片绑定请求（映射行ID、目标图片ID）") @RequestBody ImageMappingBindDTO dto) {
        log.info("Admin bind image mapping, mappingId: {}, imageId: {}", dto.getMappingId(), dto.getImageId());
        noteRelationFacade.bindImageMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/image/unbind/{mappingId}")
    @Operation(summary = "解绑图片映射",
            description = "解除指定图片映射行的图片绑定，清空 imageId 和跨用户标记，并重新判断笔记是否仍存在缺失信息。")
    public Result<String> unbindImage(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind image mapping, mappingId: {}", mappingId);
        noteRelationFacade.unbindImageMapping(mappingId);
        return Result.success();
    }

    @PutMapping("/each/bind")
    @Operation(summary = "绑定双链笔记映射",
            description = "为指定双链映射行绑定目标笔记，绑定前会校验标题匹配、目标笔记审核状态和删除状态，成功后刷新缺失信息。")
    public Result<String> bindEach(
            @Parameter(description = "双链绑定请求（映射行ID、目标笔记ID）") @RequestBody EachMappingBindDTO dto) {
        log.info("Admin bind note-each mapping, mappingId: {}, noteId: {}", dto.getMappingId(), dto.getNoteId());
        noteRelationFacade.bindEachMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/each/unbind/{mappingId}")
    @Operation(summary = "解绑双链笔记映射",
            description = "解除指定双链映射行与目标笔记的绑定关系，清空 targetNoteId 后重算笔记关联完整性。")
    public Result<String> unbindEach(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind note-each mapping, mappingId: {}", mappingId);
                noteRelationFacade.unbindEachMapping(mappingId);
        return Result.success();
    }
}
