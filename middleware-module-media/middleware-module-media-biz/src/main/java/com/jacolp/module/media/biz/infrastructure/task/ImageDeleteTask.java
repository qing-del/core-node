package com.jacolp.module.media.biz.infrastructure.task;

import com.jacolp.constant.ImageConstant;
import com.jacolp.framework.oss.AliyunOSSOperator;
import com.jacolp.module.media.biz.infrastructure.persistence.dataobject.ImageDeleteDeadLetterDO;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageDeleteDeadLetterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component @Slf4j
public class ImageDeleteTask {
    private final AliyunOSSOperator oss; private final ImageDeleteDeadLetterMapper mapper;
    public ImageDeleteTask(AliyunOSSOperator oss, ImageDeleteDeadLetterMapper mapper) { this.oss = oss; this.mapper = mapper; }
    @Scheduled(fixedRateString = "${jacolp.image.delete-image-task-time:60}", timeUnit = TimeUnit.MINUTES)
    public void deleteImageTask() {
        List<ImageDeleteDeadLetterDO> rows = mapper.selectBatch(ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING);
        if (rows == null || rows.isEmpty()) return;
        List<Long> ok = new ArrayList<>(), failed = new ArrayList<>();
        for (ImageDeleteDeadLetterDO row : rows) { if (deleteImage(row.getImageUrl())) ok.add(row.getId()); else failed.add(row.getId()); }
        if (!ok.isEmpty()) mapper.updateBatch(ok, ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_COMPLETED);
        if (!failed.isEmpty()) mapper.updateBatch(failed, ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING);
    }
    public boolean deleteImage(String url) { int at = url == null ? -1 : url.indexOf(ImageConstant.IMAGE_OSS_DIRECTORY_PREFIX); return at >= 0 && oss.delete(url.substring(at)); }
}
