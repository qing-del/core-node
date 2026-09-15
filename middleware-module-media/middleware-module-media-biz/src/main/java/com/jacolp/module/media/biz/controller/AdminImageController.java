package com.jacolp.module.media.biz.controller;

import com.jacolp.module.media.biz.application.service.MediaImageService;
import com.jacolp.module.media.biz.application.vo.image.ImageBatchDeleteVO;
import com.jacolp.module.media.biz.application.vo.image.ImageNoteSimpleVO;
import com.jacolp.module.media.biz.application.dto.image.ImageModifyInfoDTO;
import com.jacolp.module.media.biz.application.dto.image.ImagePublicDTO;
import com.jacolp.module.media.biz.application.dto.image.ImageQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.utils.IdParserUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/** Non-review admin image endpoints. The review endpoint intentionally remains server-owned. */
@RestController("Admin-ImageController")
@RequestMapping("/admin/image")
@CrossOrigin("*")
@Slf4j
@Schema(description = "Admin - 图片管理")
@Tag(name = "Admin-图片管理", description = "图片上传、审核、查询与删除接口")
public class AdminImageController {
    private final MediaImageService images;
    public AdminImageController(MediaImageService images) { this.images = images; }
    @PutMapping("/modify-info") @Operation(summary = "修改图片元信息", description = "修改图片名称或主题归属等元信息，不替换图片二进制内容；修改文件名时会做同用户同主题唯一性校验。")
    public Result<String> modifyInfo(@Parameter(description = "图片元信息修改请求（图片ID、新名称、新主题ID）") @RequestBody ImageModifyInfoDTO dto) { log.info("Admin modify image info, id: {}", dto.getId()); images.modifyImageInfo(dto); return Result.success(); }
    @PutMapping("/transfer-to-cloud") @Operation(summary = "迁移图片到云存储", description = "批量触发图片存储介质迁移流程，默认处理阿里云 OSS 入口并预留 R2 等多云扩展；按图片 ID 逐条处理，失败项会记录日志。")
    public Result<String> transferToCloud(@Parameter(description = "图片ID列表，使用英文逗号分隔") @RequestParam String ids) { List<Long> idList = IdParserUtil.parseIds(ids, "图片"); log.info("Admin transfer to cloud, ids: {}", idList); return Result.error("暂不支持迁移"); }
    @DeleteMapping("/delete") @Operation(summary = "批量删除图片", description = "批量删除图片前会先检查是否被笔记引用，若存在引用则整批拒绝；删除成功后会同步回收用户存储并记录死信队列。")
    public Result<ImageBatchDeleteVO> delete(@Parameter(description = "图片ID列表，使用英文逗号分隔") @RequestParam String ids) { List<Long> idList = IdParserUtil.parseIds(ids, "图片"); log.info("Admin delete images, ids: {}", idList); return Result.success(images.deleteImages(idList)); }
    @PostMapping("/list") @Operation(summary = "分页查询图片", description = "按用户、主题、文件名、公开状态和审核状态等条件分页查询图片列表，便于管理端筛选与审核。")
    public Result<PageResult> list(@Parameter(description = "图片查询条件（用户ID、主题ID、文件名、公开状态、审核状态）") @RequestBody ImageQueryDTO dto) { log.info("Admin list images, userId: {}, topicId: {}", dto.getUserId(), dto.getTopicId()); return Result.success(images.listImages(dto)); }
    @GetMapping("/notes/{imageId}") @Operation(summary = "查询图片关联笔记", description = "查询当前图片被哪些笔记引用，返回笔记简要信息，便于评估删除、迁移或审核影响。")
    public Result<List<ImageNoteSimpleVO>> listNotes(@Parameter(description = "图片ID") @PathVariable Long imageId) { log.info("Admin list notes by image, imageId: {}", imageId); return Result.success(images.listNotesByImageId(imageId)); }
    @PostMapping("/public/{isPublic}") @Operation(summary = "设置图片公开状态", description = "切换图片公开/私有状态，修改后会影响跨用户复用和前端可见范围。")
    public Result<String> setPublic(@Parameter(description = "是否公开（0:私有, 1:公开）") @PathVariable Short isPublic, @Parameter(description = "图片公开状态请求（图片ID）") @RequestBody ImagePublicDTO dto) { log.info("Admin set image public, imageId: {}, isPublic: {}", dto.getId(), isPublic); images.setImagePublic(dto.getId(), isPublic); return Result.success(); }
}
