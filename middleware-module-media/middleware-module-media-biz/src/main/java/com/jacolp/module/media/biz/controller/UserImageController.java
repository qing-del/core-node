package com.jacolp.module.media.biz.controller;

import com.jacolp.context.BaseContext;
import com.jacolp.module.media.biz.application.annotation.ImageLimit;
import com.jacolp.module.media.biz.application.dto.image.ImageModifyInfoDTO;
import com.jacolp.module.media.biz.application.dto.image.UserImageQueryDTO;
import com.jacolp.module.media.biz.application.service.MediaImageService;
import com.jacolp.module.media.biz.application.vo.image.ImageOverviewVO;
import com.jacolp.module.media.biz.application.vo.image.UserImageDetailVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/** User image HTTP endpoints, now owned by media. */
@RestController("User-ImageController")
@RequestMapping("/user/image")
@CrossOrigin("*")
@Slf4j
@Schema(description = "User - 图片管理")
@Tag(name = "User-图片管理", description = "用户端图片管理接口")
public class UserImageController {
    private final MediaImageService images;
    public UserImageController(MediaImageService images) { this.images = images; }
    @PostMapping("/list") @Operation(summary = "条件查询图片列表", description = "查询当前用户自己的图片 + 别人已公开的图片。支持按主题 ID、文件名筛选，分页返回。")
    public Result<PageResult> list(@Parameter(description = "用户图片查询条件（主题ID、文件名、分页参数）") @RequestBody UserImageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId(); log.info("User list images, userId: {}, topicId: {}", userId, dto.getTopicId()); return Result.success(images.listUserImages(userId, dto));
    }
    @GetMapping("/overview") @Operation(summary = "获取用户图片统计", description = "返回当前用户的图片总数和已通过审核数。")
    public Result<ImageOverviewVO> getOverview() { log.info("User get image overview"); return Result.success(images.getUserImageOverview()); }
    @PostMapping("/upload") @ImageLimit @Operation(summary = "上传图片", description = "从当前登录用户上下文获取 userId 后，将图片上传到默认对象存储并创建图片记录；上传前会先经过文件大小、后缀和存储配额校验，成功后返回可访问地址。")
    public Result<String> upload(@Parameter(description = "所属主题ID（可选）") @RequestParam(required = false) Long topicId, @Parameter(description = "图片文件") @RequestParam MultipartFile file) { log.info("User upload image, topicId: {}, filename: {}", topicId, file.getOriginalFilename()); images.uploadImage(file, topicId); return Result.success(); }
    @PutMapping("/modify-file") @ImageLimit @Operation(summary = "替换图片源文件", description = "校验图片归属与存储类型后，覆盖上传新的图片文件并更新 ossUrl 和 fileSize；当前实现仅支持已接入的云存储类型。")
    public Result<String> modifyFile(@Parameter(description = "图片ID") @RequestParam Long id, @Parameter(description = "新文件") @RequestParam MultipartFile file) { log.info("User modify image file, id: {}, filename: {}", id, file.getOriginalFilename()); images.modifyImageFile(id, file); return Result.success(); }
    @PutMapping("/modify-info") @Operation(summary = "修改图片元信息", description = "修改图片名称或主题归属等元信息，不替换图片二进制内容；修改文件名时会做同用户同主题唯一性校验。")
    public Result<String> modifyInfo(@Parameter(description = "图片元信息修改请求（图片ID、新名称、新主题ID）") @RequestBody ImageModifyInfoDTO dto) { log.info("User modify image info, id: {}", dto.getId()); images.modifyImageInfo(dto); return Result.success("修改成功"); }
    @GetMapping("/{id}") @Operation(summary = "获取图片详情", description = "根据图片ID查询图片记录，返回图片访问URL、文件名、大小、上传时间等元数据信息。")
    public Result<UserImageDetailVO> getDetail(@Parameter(description = "图片ID") @PathVariable Long id) { log.info("User get image detail: {}", id); return Result.success(images.getUserImageDetail(id)); }
    @DeleteMapping("/{id}") @Operation(summary = "删除图片", description = "根据图片ID删除图片。从云存储删除对应对象文件，并从数据库删除图片记录。仅允许删除自己的图片。")
    public Result<String> delete(@Parameter(description = "图片ID") @PathVariable Long id) { log.info("User delete image: {}", id); images.deleteImage(id); return Result.success("删除成功"); }
}
