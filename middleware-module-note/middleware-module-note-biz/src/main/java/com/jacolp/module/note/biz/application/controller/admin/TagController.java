package com.jacolp.module.note.biz.application.controller.admin;

import com.jacolp.module.note.biz.application.dto.tag.TagModifyDTO;
import com.jacolp.module.note.biz.application.dto.tag.TagQueryDTO;
import com.jacolp.module.note.biz.application.service.TagService;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.utils.IdParserUtil;
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

@RestController("Admin-TagController")
@RequestMapping("/admin/tag")
@Slf4j
@CrossOrigin("*")
@Validated
@Schema(description = "Admin - 标签管理")
@Tag(name = "Admin-标签管理", description = "标签新增、批量新增、修改、删除与分页查询接口")
public class TagController {

    @Autowired private TagService tagService;

    /**
     * 修改标签
     * 不推荐使用这个接口
     */
    @PutMapping("/modify")
    @Operation(summary = "修改标签",
            description = "修改标签名称前会先按 userId 校验标签归属与存在性，并再次检查新名称是否与当前用户已有标签冲突。")
    public Result<String> modify(
            @Parameter(description = "标签修改请求（标签ID、新名称）") @RequestBody TagModifyDTO dto) {
        log.info("Admin modify tag, id: {}", dto.getId());
        tagService.modifyTag(dto);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除标签",
            description = "批量删除前会先检查所有目标标签是否存在，并查询其被笔记引用的数量；只要有任一标签仍被使用，整批删除即拒绝执行。")
    public Result<String> delete(@Parameter(description = "标签ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam @NotBlank(message = "待删除的标签 ID 列表不能为空") String ids) {
        List<Long> idList = IdParserUtil.parseIds(ids, "标签");
        log.info("Admin delete tags, ids: {}", idList);
        tagService.deleteTags(idList);
        return Result.success();
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询标签",
            description = "按关键词前缀进行模糊匹配并分页返回标签列表，支持按当前用户或指定用户维度查询。")
    public Result<PageResult> list(
            @Parameter(description = "标签查询条件（关键词、用户ID、分页参数）") @RequestBody TagQueryDTO dto) {
        return Result.success(tagService.listTags(dto));
    }
}
