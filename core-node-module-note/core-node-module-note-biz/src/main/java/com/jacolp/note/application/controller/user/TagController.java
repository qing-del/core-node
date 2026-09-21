package com.jacolp.note.application.controller.user;

import java.util.List;

import com.jacolp.common.core.result.PageResult;
import com.jacolp.common.core.result.Result;
import com.jacolp.common.core.utils.IdParserUtil;
import com.jacolp.note.application.dto.tag.*;
import com.jacolp.note.application.vo.tag.TagBatchAddVO;
import com.jacolp.note.application.service.TagService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.jacolp.note.application.vo.tag.TagStatsVO;
import com.jacolp.note.application.vo.tag.UserTagSimpleVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户端标签控制器
 * <p>提供用户端的标签查询、创建、删除、绑定等功能接口</p>
 */
@RestController("User-TagController")
@RequestMapping("/user/tag")
@Slf4j
@Validated
@Schema(description = "User - 标签管理")
@Tag(name = "User-标签管理", description = "用户端标签管理接口")
public class TagController {

    @Autowired private TagService tagService;

    /**
     * 条件查询标签列表
     * <p>查询当前用户自己的标签 + 别人已通过审核的标签。支持按关键字模糊搜索，分页返回。</p>
     *
     * @param dto 查询条件，包含关键字、分页参数等
     * @return 分页后的标签列表
     */
    @PostMapping("/list")
    @Operation(summary = "条件查询标签列表",
            description = "查询当前用户自己的标签 + 别人已通过审核的标签。支持按关键字模糊搜索，分页返回。")
    public Result<PageResult> list(
            @Parameter(description = "标签查询条件（关键词、分页参数）") @RequestBody UserTagQueryDTO dto) {
        log.info("User list tags, keyword: {}", dto.getKeyword());
        return Result.success(tagService.listUserTags(dto));
    }

    /**
     * 获取当前用户标签统计。
     */
    @GetMapping("/stats")
    @Operation(summary = "获取用户标签统计",
            description = "返回当前用户的标签总数和已通过审核数。")
    public Result<TagStatsVO> getStats() {
        log.info("User get tag stats");
        return Result.success(tagService.getUserTagStats());
    }

    /**
     * 查询当前用户的标签列表
     * <p>查询当前登录用户创建的所有标签，返回标签的基本信息。</p>
     *
     * @return 当前用户的所有标签列表
     */
    @GetMapping
    @Operation(summary = "查询当前用户标签列表", description = "查询当前登录用户创建的所有标签，返回标签的基本信息（ID、名称、创建时间）。")
    public Result<List<UserTagSimpleVO>> listMyTags() {
        log.info("User query tag list");
        return Result.success(tagService.listUserTagSimples());
    }

    /**
     * 新增标签
     * <p>从当前登录用户上下文获取 userId 后创建单个标签；服务层会先清洗名称、校验长度，再检查同名标签是否已存在，避免重复创建。</p>
     *
     * @param dto 新增标签请求，包含标签名称
     * @return 新增结果提示
     */
    @PostMapping("/add")
    @Operation(summary = "新增标签",
            description = "从当前登录用户上下文获取 userId 后创建单个标签；服务层会先清洗名称、校验长度，再检查同名标签是否已存在，避免重复创建。")
    public Result<String> add(
            @Parameter(description = "新增标签请求（标签名称）") @RequestBody TagAddDTO dto) {
        log.info("User add tag, tagName: {}", dto.getTagName());
        tagService.addTag(dto);
        return Result.success();
    }

    @PostMapping("/batch-add")
    @Operation(summary = "批量新增标签",
            description = "批量创建标签时先去重并过滤空值，再对比当前用户已有标签列表；仅插入不存在的标签，返回成功数量和已存在标签列表。")
    public Result<TagBatchAddVO> batchAdd(
            @Parameter(description = "批量新增标签请求（标签名称列表）") @RequestBody @Valid TagBatchAddDTO dto) {
        log.info("User batch add tag, tagNames: {}", dto.getTagNames());
        return Result.success(tagService.batchAddTags(dto));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除标签",
            description = "批量删除前会先检查所有目标标签是否存在，并查询其被笔记引用的数量；只要有任一标签仍被使用，整批删除即拒绝执行。")
    public Result<String> delete(@Parameter(description = "标签ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam @NotBlank(message = "待删除的标签 ID 列表不能为空") String ids) {
        List<Long> idList = IdParserUtil.parseIds(ids, "标签");
        log.info("User delete tags, ids: {}", idList);
        tagService.deleteTags(idList);
        return Result.success();
    }

    /**
     * 绑定标签到资源
     * <p>将标签绑定到笔记。绑定前会校验标签归属和目标资源的存在性。</p>
     *
     * @param dto 绑定请求，包含标签ID、目标资源ID、目标资源类型
     * @return 绑定结果提示
     */
    @PostMapping("/assign")
    @Operation(summary = "绑定标签到资源",
            description = "将标签绑定到笔记或主题。绑定前会校验标签归属和目标资源的存在性。")
    public Result<String> assign(
            @Parameter(description = "标签绑定请求（标签ID、目标资源ID、目标资源类型）") @RequestBody @Valid UserTagAssignDTO dto) {
        log.info("User assign tag {} to note {}", dto.getTagId(), dto.getTargetId());
        tagService.assignUserTag(dto);
        return Result.success("绑定成功");
    }

    /**
     * 解除标签绑定
     * <p>解除标签与笔记的绑定关系。仅允许操作自己的标签和资源。</p>
     *
     * @param dto 解除绑定请求，包含标签ID、目标资源ID、目标资源类型
     * @return 解除绑定结果提示
     */
    @PostMapping("/remove")
    @Operation(summary = "解除标签绑定", description = "解除标签与笔记之间的绑定关系。仅允许操作自己的标签和资源。")
    public Result<String> remove(
            @Parameter(description = "标签解绑请求（标签ID、目标资源ID、目标资源类型）") @RequestBody @Valid UserTagRemoveDTO dto) {
        log.info("User remove tag {} from note {}", dto.getTagId(), dto.getTargetId());
        tagService.removeUserTag(dto);
        return Result.success("解除绑定成功");
    }
}
