package com.jacolp.module.note.biz.interfaces.web.user;

import com.jacolp.module.note.biz.application.dto.topic.TopicAddDTO;
import com.jacolp.module.note.biz.application.dto.topic.TopicModifyDTO;
import com.jacolp.module.note.biz.application.vo.topic.TopicListVO;
import com.jacolp.module.note.biz.application.dto.topic.UserTopicQueryDTO;
import com.jacolp.module.note.biz.application.service.TopicService;
import com.jacolp.module.note.biz.application.vo.topic.TopicStatsVO;
import com.jacolp.utils.IdParserUtil;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.jacolp.result.PageResult;
import com.jacolp.result.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@RestController("User-TopicController")
@RequestMapping("/user/topic")
@Slf4j
@CrossOrigin("*")
@Schema(description = "User - 主题管理")
@Tag(name = "User-主题管理", description = "用户端主题条件查询与审核申请接口")
public class TopicController {

    @Autowired private TopicService topicService;

    @PostMapping("/list")
    @Operation(summary = "条件查询主题",
            description = "查询当前用户自己的主题 + 别人已通过审核的主题。支持按关键字模糊搜索，分页返回。")
    public Result<PageResult> list(
            @Parameter(description = "主题查询条件（关键词、分页参数）") @RequestBody UserTopicQueryDTO dto) {
        log.info("User list topics, keyword: {}", dto.getKeyword());
        return Result.success(topicService.listUserTopics(dto));
    }

    @GetMapping("/children")
    @Operation(summary = "查询当前用户指定父级下的主题目录",
            description = "parentId 为空时查询一级主题目录；parentId 非空时查询该主题下的一层子目录。")
    public Result<List<TopicListVO>> listChildren(
            @Parameter(description = "父级主题ID，不传表示一级目录") @RequestParam(required = false) Long parentId) {
        log.info("User list topic children, parentId: {}", parentId);
        return Result.success(topicService.listChildren(parentId));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取用户主题统计",
            description = "返回当前用户的主题总数和已通过审核数。")
    public Result<TopicStatsVO> getStats() {
        log.info("User get topic stats");
        return Result.success(topicService.getUserTopicStats());
    }

    @PostMapping("/add")
    @Operation(summary = "新增主题",
            description = "从当前登录用户上下文获取 userId 后创建主题；服务层会先清洗主题名、校验长度，再检查同一用户下主题名称唯一性。")
    public Result<String> add(
            @Parameter(description = "新增主题请求（主题名称）") @RequestBody TopicAddDTO dto) {
        log.info("User add topic, topicName: {}", dto.getTopicName());
        topicService.addTopic(dto);
        return Result.success();
    }

    @PutMapping("/modify")
    @Operation(summary = "修改主题", description = "修改主题的排序等级。")
    public Result<String> modify(
            @Parameter(description = "修改主题请求（主题ID、排序等级）") @RequestBody TopicModifyDTO dto) {
        log.info("User modify topic, id: {}", dto.getId());
        topicService.modifyTopic(dto);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除主题",
            description = "批量删除前会先验证所有主题是否存在，并检查每个主题下是否存在未删除笔记；只要存在引用，整批删除即拒绝。")
    public Result<String> delete(@Parameter(description = "主题ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam String ids) {
        // 支持前端以字符串形式传入批量 ID："1,2,3"
        List<Long> idList = IdParserUtil.parseIds(ids, "主题");
        log.info("User delete topics, ids: {}", idList);
        topicService.deleteTopics(idList);
        return Result.success();
    }
}
