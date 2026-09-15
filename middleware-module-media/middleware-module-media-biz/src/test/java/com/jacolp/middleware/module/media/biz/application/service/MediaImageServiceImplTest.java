package com.jacolp.middleware.module.media.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.context.BaseContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.framework.oss.AliyunOSSOperator;
import com.jacolp.middleware.messaging.pulisher.MediaResourceDeleteEventPublisher;
import com.jacolp.middleware.messaging.pulisher.StorageReleasedEventPublisher;
import com.jacolp.module.audit.api.AuditApplicationApi;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.module.audit.api.CreateAuditApplicationCommand;
import com.jacolp.module.media.biz.application.service.MediaImageServiceImpl;
import com.jacolp.module.media.biz.infrastructure.persistence.dataobject.ImageDO;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageDeleteDeadLetterMapper;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import com.jacolp.module.note.api.NoteReadApi;
import com.jacolp.module.note.api.TopicQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MediaImageServiceImplTest {

    @AfterEach
    void clearContext() {
        BaseContext.remove();
    }

    @Test
    void submitAndCancelUseAuditApiAroundImageCas() {
        ImageMapper mapper = mock(ImageMapper.class);
        AuditApplicationApi auditApi = mock(AuditApplicationApi.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectById(7L)).thenReturn(image(7L, 9L, AuditStatus.WAIT));
        when(mapper.updateAuditStatusIfCurrent(7L, AuditStatus.WAIT.getCode(),
                AuditStatus.AUDITING.getCode())).thenReturn(1);

        service(mapper, auditApi).submitImageAudit(7L);

        ArgumentCaptor<CreateAuditApplicationCommand> create = ArgumentCaptor.forClass(CreateAuditApplicationCommand.class);
        verify(auditApi).createApplication(create.capture());
        assertEquals(AuditTargetType.IMAGE, create.getValue().targetType());
        assertEquals("image-7.png", create.getValue().targetName());

        when(mapper.selectById(7L)).thenReturn(image(7L, 9L, AuditStatus.AUDITING));
        when(mapper.updateAuditStatusIfCurrent(7L, AuditStatus.AUDITING.getCode(),
                AuditStatus.WAIT.getCode())).thenReturn(1);

        service(mapper, auditApi).cancelImageAudit(7L);

        ArgumentCaptor<CancelAuditApplicationCommand> cancel = ArgumentCaptor.forClass(CancelAuditApplicationCommand.class);
        verify(auditApi).cancelApplication(cancel.capture());
        assertEquals(AuditTargetType.IMAGE, cancel.getValue().targetType());
        assertEquals(9L, cancel.getValue().actorUserId());
    }

    private static MediaImageServiceImpl service(ImageMapper mapper, AuditApplicationApi auditApi) {
        return new MediaImageServiceImpl(mapper, mock(ImageDeleteDeadLetterMapper.class), mock(AliyunOSSOperator.class),
                mock(NoteReadApi.class), mock(TopicQueryApi.class), auditApi,
                mock(StorageReleasedEventPublisher.class), mock(MediaResourceDeleteEventPublisher.class));
    }

    private static ImageDO image(Long id, Long userId, AuditStatus status) {
        ImageDO image = new ImageDO();
        image.setId(id);
        image.setUserId(userId);
        image.setFilename("image-" + id + ".png");
        image.setOssUrl("https://example.test/image-" + id);
        image.setAuditStatus(status.getCode());
        return image;
    }
}
