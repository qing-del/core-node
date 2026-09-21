package com.jacolp.media.application.service;

import com.jacolp.media.api.model.MediaFileSummary;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.media.application.dto.image.ImageModifyInfoDTO;
import com.jacolp.media.application.dto.image.ImageQueryDTO;
import com.jacolp.media.application.dto.image.UserImageQueryDTO;
import com.jacolp.media.application.vo.image.ImageBatchDeleteVO;
import com.jacolp.media.application.vo.image.ImageOverviewVO;
import com.jacolp.media.application.vo.image.ImageVO;
import com.jacolp.media.application.vo.image.UserImageDetailVO;
import com.jacolp.media.application.vo.image.ImageNoteSimpleVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Application boundary for image operations owned by the media module. */
public interface MediaImageService {
    ImageVO uploadImage(MultipartFile file, Long topicId);
    void modifyImageFile(Long id, MultipartFile file);
    void modifyImageInfo(ImageModifyInfoDTO dto);
    ImageBatchDeleteVO deleteImages(List<Long> ids);
    PageResult listImages(ImageQueryDTO dto);
    PageResult listUserImages(Long userId, UserImageQueryDTO dto);
    List<ImageNoteSimpleVO> listNotesByImageId(Long imageId);
    void transferToCloud(List<Long> ids);
    void setImagePublic(Long imageId, Short isPublic);
    ImageOverviewVO getUserImageOverview();
    UserImageDetailVO getUserImageDetail(Long id);
    void deleteImage(Long id);
    void submitImageAudit(Long id);
    void cancelImageAudit(Long id);
    MediaFileSummary findRequired(Long id);
}
