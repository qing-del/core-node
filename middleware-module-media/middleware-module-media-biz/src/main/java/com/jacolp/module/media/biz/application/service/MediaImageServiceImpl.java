package com.jacolp.module.media.biz.application.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.ImageConstant;
import com.jacolp.constant.ScopeConstant;
import com.jacolp.constant.TopicConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.framework.oss.AliyunOSSOperator;
import com.jacolp.middleware.messaging.pulisher.MediaResourceDeleteEventPublisher;
import com.jacolp.middleware.messaging.event.MediaResourceDeleteRequestedEvent;
import com.jacolp.middleware.messaging.event.StorageReleasedEvent;
import com.jacolp.middleware.messaging.pulisher.StorageReleasedEventPublisher;
import com.jacolp.module.media.api.model.MediaFileSummary;
import com.jacolp.module.media.api.model.MediaReviewStatus;
import com.jacolp.module.audit.api.AuditApplicationApi;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.module.audit.api.CreateAuditApplicationCommand;
import com.jacolp.module.media.biz.application.dto.image.ImageModifyInfoDTO;
import com.jacolp.module.media.biz.application.dto.image.ImageQueryDTO;
import com.jacolp.module.media.biz.application.dto.image.UserImageQueryDTO;
import com.jacolp.module.media.biz.infrastructure.persistence.dataobject.ImageDO;
import com.jacolp.module.media.biz.infrastructure.persistence.dataobject.ImageDeleteDeadLetterDO;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageDeleteDeadLetterMapper;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import com.jacolp.module.note.api.NoteReadApi;
import com.jacolp.module.note.api.TopicQueryApi;
import com.jacolp.module.system.api.quota.StorageHandler;
import com.jacolp.module.system.api.quota.StorageOperationType;
import com.jacolp.module.media.biz.application.vo.image.*;
import com.jacolp.result.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Image application service migrated from the legacy server implementation.
 * The method flow and externally observable failures intentionally match the old service.
 */
@Service
@Slf4j
public class MediaImageServiceImpl implements MediaImageService {
    private final ImageMapper imageMapper;
    private final ImageDeleteDeadLetterMapper imageDeleteDeadLetterMapper;
    private final AliyunOSSOperator aliyunOSSOperator;
    private final NoteReadApi noteReadApi;
    private final TopicQueryApi topicQueryApi;
    private final AuditApplicationApi auditApi;
    private final StorageReleasedEventPublisher storageReleasedEventPublisher;
    private final MediaResourceDeleteEventPublisher mediaResourceDeleteEventPublisher;

    public MediaImageServiceImpl(ImageMapper imageMapper, ImageDeleteDeadLetterMapper imageDeleteDeadLetterMapper,
                                 AliyunOSSOperator aliyunOSSOperator, NoteReadApi noteReadApi,
                                 TopicQueryApi topicQueryApi, AuditApplicationApi auditApi,
                                 StorageReleasedEventPublisher storageReleasedEventPublisher,
                                 MediaResourceDeleteEventPublisher mediaResourceDeleteEventPublisher) {
        this.imageMapper = imageMapper;
        this.imageDeleteDeadLetterMapper = imageDeleteDeadLetterMapper;
        this.aliyunOSSOperator = aliyunOSSOperator;
        this.noteReadApi = noteReadApi;
        this.topicQueryApi = topicQueryApi;
        this.auditApi = auditApi;
        this.storageReleasedEventPublisher = storageReleasedEventPublisher;
        this.mediaResourceDeleteEventPublisher = mediaResourceDeleteEventPublisher;
    }

    @Override
    @StorageHandler(operationType = StorageOperationType.UPLOAD)
    public ImageVO uploadImage(MultipartFile file, Long topicId) {
        Long userId = BaseContext.getCurrentId();
        String filename = file.getOriginalFilename();
        validateFilename(filename);
        validateTopic(topicId);

        int count = imageMapper.countByUserIdTopicIdAndFilename(userId, topicId, filename);
        if (count > 0) throw new BaseException(ImageConstant.IMAGE_NAME_DUPLICATE);

        String ossUrl = uploadToAliyunOss(file, userId, filename);
        ImageDO image = buildImageEntity(file.getSize(), topicId, userId, filename, ossUrl);
        int insertCount = imageMapper.insertImage(image);
        if (insertCount <= 0) throw new BaseException("图片上传失败");

        ImageVO vo = new ImageVO();
        BeanUtils.copyProperties(image, vo);
        vo.setId(null);
        return vo;
    }

    @Override
    @StorageHandler(operationType = StorageOperationType.MODIFY)
    public void modifyImageFile(Long id, MultipartFile newFile) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(id);
        ImageDO existed = imageMapper.selectById(id);
        if (existed == null) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        if (!existed.getUserId().equals(userId)) throw new BaseException(ImageConstant.IMAGE_NOT_OWNER);
        if (existed.getStorageType() == null || existed.getStorageType() != ImageConstant.STORAGE_TYPE_ALIYUN_OSS) {
            throw new BaseException(ImageConstant.IMAGE_STORAGE_PROVIDER_NOT_SUPPORTED);
        }

        Long newFileSize = newFile.getSize();
        String oldObjectKey = extractObjectKeyFromUrl(existed.getOssUrl());
        String newOssUrl = uploadToAliyunOss(newFile, oldObjectKey);
        existed.setOssUrl(newOssUrl);
        existed.setFileSize(newFileSize);
        if (imageMapper.updateImage(existed) <= 0) throw new BaseException("图片更新失败");
    }

    @Override
    public void modifyImageInfo(ImageModifyInfoDTO dto) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(dto.getId());
        validateTopic(dto.getTopicId());
        ImageDO existed = imageMapper.selectById(dto.getId());
        if (existed == null || !existed.getUserId().equals(userId)) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        if (!PermissionContext.isAdmin() && !existed.getUserId().equals(userId)) throw new BaseException(ImageConstant.IMAGE_NOT_OWNER);

        if (dto.getTopicId() != null) existed.setTopicId(dto.getTopicId());
        if (imageMapper.updateImage(existed) <= 0) throw new BaseException("图片信息更新失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferToCloud(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        int successCount = 0;
        int failureCount = 0;
        for (Long imageId : ids) {
            try {
                ImageDO image = imageMapper.selectById(imageId);
                if (image == null) continue;
                if (image.getStorageType() != null && image.getStorageType() == ImageConstant.STORAGE_TYPE_ALIYUN_OSS) continue;
                log.warn("暂不支持从当前存储类型迁移到阿里云 OSS，imageId: {}, storageType: {}", imageId, image.getStorageType());
                failureCount++;
            } catch (Exception e) {
                log.error("转移到云存储失败，imageId: {}", imageId, e);
                failureCount++;
            }
        }
        log.info("转移到云存储完成，成功: {}, 失败: {}", successCount, failureCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageBatchDeleteVO deleteImages(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BaseException("待删除的图片 ID 列表不能为空");
        Set<Long> idSet = new LinkedHashSet<>(ids);
        List<ImageDO> images = imageMapper.selectByIds(new ArrayList<>(idSet));
        Map<Long, Long> referenceCounts = noteReadApi.countMediaReferencesByMediaIds(idSet);
        List<String> inUseImageNames = new ArrayList<>();
        for (ImageDO image : images) if (referenceCounts.getOrDefault(image.getId(), 0L) > 0) inUseImageNames.add(image.getFilename());
        if (!inUseImageNames.isEmpty()) throw new BaseException(ImageConstant.IMAGE_IN_USE + "：" + String.join(", ", inUseImageNames));

        ImageBatchDeleteVO result = new ImageBatchDeleteVO(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (ImageDO image : images) {
            AuditStatus status = AuditStatus.fromCode(image.getAuditStatus());
            if (!status.canTransitionTo(AuditStatus.DELETED)) throw new BaseException("审核中的图片不能删除");
        }
        images.forEach(image -> {
            result.getSuccessIds().add(image.getId());
            result.getSuccessFileNames().add(image.getFilename());
        });
        List<MediaResourceDeleteRequestedEvent> deleteRequests = trackPhysicalDeletes(images);
        if (imageMapper.deleteByIds(result.getSuccessIds()) < idSet.size()) throw new BaseException("图片删除失败");
        mediaResourceDeleteEventPublisher.publish(deleteRequests);
        storageReleasedEventPublisher.publish(images.stream()
                .filter(image -> image.getFileSize() != null && image.getFileSize() > 0)
                .map(image -> new StorageReleasedEvent(image.getUserId(), "IMAGE",
                        String.valueOf(image.getId()), image.getFileSize()))
                .toList());
        return result;
    }

    @Override
    public PageResult listImages(ImageQueryDTO dto) {
        if (dto == null) dto = new ImageQueryDTO();
        ImageDO query = new ImageDO();
        BeanUtils.copyProperties(dto, query);
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<ImageVO> records = imageMapper.listByCondition(query);
        PageInfo<ImageVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public List<ImageNoteSimpleVO> listNotesByImageId(Long imageId) {
        validateImageId(imageId);
        return noteReadApi.findNoteMediaReferenceSummariesByMediaIds(List.of(imageId)).getOrDefault(imageId, List.of()).stream()
                .map(note -> new ImageNoteSimpleVO(note.id(), note.title(), note.isCrossUser(), note.status(), note.createTime()))
                .toList();
    }

    @Override
    public void setImagePublic(Long imageId, Short isPublic) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(imageId);
        ImageDO image = imageMapper.selectById(imageId);
        if (image == null || (!PermissionContext.isAdmin() && !image.getUserId().equals(userId))) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        image.setIsPublic(isPublic);
        if (imageMapper.updateImage(image) <= 0) throw new BaseException("图片公开状态更新失败");
    }

    @Override
    public PageResult listUserImages(Long userId, UserImageQueryDTO dto) {
        if (dto == null) dto = new UserImageQueryDTO();
        String filename = dto.getFilename() != null && !dto.getFilename().trim().isEmpty() ? dto.getFilename().trim() : null;
        boolean globalScope = ScopeConstant.SCOPE_GLOBAL.equals(dto.getScope());
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<ImageVO> records = imageMapper.listByUserCondition(userId, dto.getTopicId(), filename, globalScope);
        PageInfo<ImageVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitImageAudit(Long imageId) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(imageId);
        ImageDO image = imageMapper.selectById(imageId);
        if (image == null) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        if (!image.getUserId().equals(userId)) throw new BaseException("只能申请审核自己的图片");
        AuditStatus status = AuditStatus.fromCode(image.getAuditStatus());
        if (!status.canTransitionTo(AuditStatus.AUDITING)) throw new BaseException("该图片已通过审核");
        if (imageMapper.updateAuditStatusIfCurrent(imageId, status.getCode(), AuditStatus.AUDITING.getCode()) != 1) {
            throw new BaseException("该图片已有处理中的审核命令");
        }
        auditApi.createApplication(new CreateAuditApplicationCommand(AuditTargetType.IMAGE, imageId, userId,
                null, image.getFilename(), image.getOssUrl()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelImageAudit(Long imageId) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(imageId);
        ImageDO image = imageMapper.selectById(imageId);
        if (image == null) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        if (!image.getUserId().equals(userId)) throw new BaseException("只能撤销自己图片的审核申请");
        AuditStatus status = AuditStatus.fromCode(image.getAuditStatus());
        if (!status.canTransitionTo(AuditStatus.WAIT)) throw new BaseException("该图片未处于审核中");
        auditApi.cancelApplication(new CancelAuditApplicationCommand(AuditTargetType.IMAGE, imageId, userId));
        if (imageMapper.updateAuditStatusIfCurrent(imageId, AuditStatus.AUDITING.getCode(),
                AuditStatus.WAIT.getCode()) != 1) {
            throw new BaseException("该图片已有处理中的审核命令");
        }
    }

    @Override
    public ImageOverviewVO getUserImageOverview() {
        Long userId = BaseContext.getCurrentId();
        return new ImageOverviewVO(imageMapper.countByUserId(userId), imageMapper.countPassedByUserId(userId));
    }

    @Override
    public UserImageDetailVO getUserImageDetail(Long id) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(id);
        ImageDO image = imageMapper.selectById(id);
        if (image == null) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        if (!image.getUserId().equals(userId)) throw new BaseException("只能查看自己的图片");
        UserImageDetailVO vo = new UserImageDetailVO();
        BeanUtils.copyProperties(image, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImage(Long id) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(id);
        ImageDO image = imageMapper.selectById(id);
        if (image == null) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        if (!image.getUserId().equals(userId)) throw new BaseException(ImageConstant.IMAGE_NOT_OWNER);
        AuditStatus status = AuditStatus.fromCode(image.getAuditStatus());
        if (!status.canTransitionTo(AuditStatus.DELETED)) throw new BaseException("审核中的图片不能删除");
        if (noteReadApi.countMediaReferencesByMediaIds(List.of(id)).getOrDefault(id, 0L) > 0) throw new BaseException(ImageConstant.IMAGE_IN_USE);
        List<MediaResourceDeleteRequestedEvent> deleteRequests = trackPhysicalDeletes(List.of(image));
        if (imageMapper.deleteByIds(List.of(id)) <= 0) throw new BaseException("删除图片失败");
        mediaResourceDeleteEventPublisher.publish(deleteRequests);
        if (image.getFileSize() != null && image.getFileSize() > 0) {
            storageReleasedEventPublisher.publish(List.of(new StorageReleasedEvent(userId, "IMAGE",
                    String.valueOf(image.getId()), image.getFileSize())));
        }
    }

    @Override
    public MediaFileSummary findRequired(Long id) {
        ImageDO image = imageMapper.selectById(id);
        if (image == null) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        return new MediaFileSummary(image.getId(), image.getUserId(), image.getTopicId(), image.getFilename(), image.getOssUrl(),
                image.getFileSize(), Short.valueOf((short) 1).equals(image.getIsPublic()), toMediaStatus(image.getAuditStatus()));
    }

    private String uploadToAliyunOss(MultipartFile file, Long userId, String filename) {
        try { return uploadToAliyunOss(file, buildObjectKey(userId, filename)); }
        catch (BaseException e) { throw e; }
        catch (Exception e) { log.error("上传文件到阿里云 OSS 失败，userId: {}, filename: {}", userId, filename, e); throw new BaseException(ImageConstant.IMAGE_TRANSFER_FAILED); }
    }
    private String uploadToAliyunOss(MultipartFile file, String objectKey) {
        try {
            String ossUrl = aliyunOSSOperator.uploadByObjectName(file.getBytes(), objectKey);
            log.info("上传到阿里云 OSS 成功，objectKey: {}, ossUrl: {}", objectKey, ossUrl);
            return ossUrl;
        } catch (Exception e) { log.error("上传到阿里云 OSS 失败，objectKey: {}", objectKey, e); throw new BaseException(ImageConstant.IMAGE_TRANSFER_FAILED); }
    }
    private String buildObjectKey(Long userId, String filename) { return ImageConstant.IMAGE_OSS_DIRECTORY_PREFIX + "/" + userId + "/" + UUID.randomUUID() + filename.substring(filename.lastIndexOf('.')); }
    private String extractObjectKeyFromUrl(String ossUrl) {
        if (ossUrl == null || ossUrl.isEmpty()) return null;
        int index = ossUrl.indexOf(ImageConstant.IMAGE_OSS_DIRECTORY_PREFIX);
        if (index < 0 || index + 1 >= ossUrl.length()) return null;
        return ossUrl.substring(index);
    }
    private void validateFilename(String filename) { if (filename == null || filename.isEmpty()) throw new BaseException(ImageConstant.IMAGE_EMPTY_FILENAME); }
    private void validateImageId(Long id) { if (id == null || id <= 0) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND); }
    private void validateTopic(Long topicId) {
        if (topicId != null && topicId > 0 && !topicQueryApi.isOwnedBy(topicId, BaseContext.getCurrentId())) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }
    }
    private @NonNull ImageDO buildImageEntity(Long fileSize, Long topicId, Long userId, String filename, String ossUrl) {
        ImageDO image = new ImageDO();
        image.setUserId(userId); image.setTopicId(topicId); image.setFilename(filename); image.setOssUrl(ossUrl);
        image.setStorageType(ImageConstant.DEFAULT_STORAGE_TYPE); image.setFileSize(fileSize); image.setIsPublic(ImageConstant.IS_PUBLIC_NO);
        image.setAuditStatus(AuditStatus.WAIT.getCode()); image.setUploadTime(LocalDateTime.now());
        return image;
    }
    private List<MediaResourceDeleteRequestedEvent> trackPhysicalDeletes(List<ImageDO> images) {
        List<MediaResourceDeleteRequestedEvent> events = new ArrayList<>();
        for (ImageDO image : images) {
            if (!Objects.equals(image.getStorageType(), ImageConstant.STORAGE_TYPE_ALIYUN_OSS)) continue;
            String objectKey = extractObjectKeyFromUrl(image.getOssUrl());
            if (objectKey == null) throw new BaseException("图片物理地址无效，无法安全删除");
            ImageDeleteDeadLetterDO tracking = new ImageDeleteDeadLetterDO();
            tracking.setResourceId(String.valueOf(image.getId()));
            tracking.setImageUrl(image.getOssUrl());
            tracking.setStatus(ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_QUEUED);
            if (imageDeleteDeadLetterMapper.insert(tracking) != 1 || tracking.getId() == null) {
                throw new BaseException("图片删除追踪记录创建失败");
            }
            events.add(new MediaResourceDeleteRequestedEvent(String.valueOf(image.getId()), objectKey,
                    tracking.getId()));
        }
        return events;
    }
    private static MediaReviewStatus toMediaStatus(Short status) {
        return switch (status) { case 0 -> MediaReviewStatus.WAITING; case 1 -> MediaReviewStatus.REVIEWING; case 2 -> MediaReviewStatus.APPROVED; case 3 -> MediaReviewStatus.REJECTED; case 4 -> MediaReviewStatus.DELETED; default -> throw new IllegalArgumentException("Unsupported legacy media status: " + status); };
    }
}
