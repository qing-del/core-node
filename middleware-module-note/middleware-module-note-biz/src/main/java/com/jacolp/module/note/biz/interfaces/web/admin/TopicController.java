package com.jacolp.module.note.biz.interfaces.web.admin;

import com.jacolp.module.note.biz.application.dto.topic.TopicListDTO;
import com.jacolp.module.note.biz.application.vo.topic.TopicDetailVO;
import com.jacolp.module.note.biz.application.vo.topic.TopicListVO;
import com.jacolp.module.note.biz.application.service.TopicService;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.utils.IdParserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("Admin-TopicController")
@RequestMapping("/admin/topic")
@Slf4j
@CrossOrigin("*")
@Schema(description = "Admin - 主题管理")
@Tag(name = "Admin-主题管理", description = "主题新增、修改、详情、分页和删除接口")
/**
 * Admin 端主题控制器。
 *
 * 说明：
 * - 本控制器仅负责参数接收与返回封装。
 * - 核心业务规则统一下沉到 Service 层。
 */
public class TopicController {

    @Autowired private TopicService topicService;

    @GetMapping("/{id}")
    @Operation(summary = "查询主题详情",
            description = "根据主题 ID 读取单条主题信息，用于编辑页回显或详情展示；若主题不存在则按业务规则返回不存在。")
    public Result<TopicDetailVO> getById(
            @Parameter(description = "主题ID") @PathVariable Long id) {
        log.info("Admin get topic by id: {}", id);
        return Result.success(topicService.getTopicById(id));
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询主题",
            description = "按当前用户或指定用户、关键字和分页参数查询主题列表，返回结果按服务层默认排序规则展示。")
    public Result<PageResult> list(
            @Parameter(description = "主题查询条件（用户ID、关键词、分页参数）") @RequestBody TopicListDTO dto) {
        return Result.success(topicService.listTopics(dto));
    }

    @GetMapping("/children")
    @Operation(summary = "查询指定用户的一层子主题目录",
            description = "parentId 不传时查询指定用户的一级目录；传入 parentId 时查询该父目录下的一层子目录。")
    public Result<List<TopicListVO>> children(
            @Parameter(description = "目标用户ID") @RequestParam Long userId,
            @Parameter(description = "父级主题ID，不传表示根目录") @RequestParam(required = false) Long parentId) {
        log.info("Admin list topic children, userId: {}, parentId: {}", userId, parentId);
        return Result.success(topicService.listChildrenByUserId(userId, parentId));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除主题",
            description = "批量删除前会先验证所有主题是否存在，并检查每个主题下是否存在未删除笔记；只要存在引用，整批删除即拒绝。")
    public Result<String> delete(@Parameter(description = "主题ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam String ids) {
        // 支持前端以字符串形式传入批量 ID："1,2,3"
        List<Long> idList = IdParserUtil.parseIds(ids, "主题");
        log.info("Admin delete topics, ids: {}", idList);
        topicService.deleteTopics(idList);
        return Result.success();
    }
}
