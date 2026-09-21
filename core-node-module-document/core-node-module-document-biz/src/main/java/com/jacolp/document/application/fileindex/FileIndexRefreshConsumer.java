package com.jacolp.document.application.fileindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Rabbit file 刷新消费者；处理时重新读取 MySQL/API 当前事实，失败复用现有 retry/DLQ。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document.file-index", name = "enabled", havingValue = "true")
public class FileIndexRefreshConsumer {

    private static final Logger log = LoggerFactory.getLogger(FileIndexRefreshConsumer.class);

    private final ObjectMapper objectMapper;
    private final FileIndexProjectionService projectionService;
    private final EventRetryPublisher retryPublisher;

    /** 保存消息解码、当前事实投影和通用重试依赖。 */
    public FileIndexRefreshConsumer(ObjectMapper objectMapper, FileIndexProjectionService projectionService,
                                    EventRetryPublisher retryPublisher) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService must not be null");
        this.retryPublisher = Objects.requireNonNull(retryPublisher, "retryPublisher must not be null");
    }

    /** 只根据资源定位重建当前投影；重复消息写同一个稳定 ID，天然幂等。 */
    @RabbitListener(queues = FileIndexCanalTopology.QUEUE)
    public void onMessage(Message message) {
        try {
            FileIndexRefreshMessage event = decode(message);
            projectionService.refresh(event.resourceKey());
        } catch (RuntimeException failure) {
            boolean retrying = retryPublisher.retryOrDeadLetter(FileIndexCanalTopology.QUEUE, message, failure);
            log.warn("file index refresh failed retrying={} reason={}", retrying, safeMessage(failure));
        }
    }

    private FileIndexRefreshMessage decode(Message message) {
        if (message == null || message.getBody() == null) {
            throw new IllegalArgumentException("file index refresh message body must not be null");
        }
        try {
            return objectMapper.readValue(message.getBody(), FileIndexRefreshMessage.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid file index refresh message", exception);
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message;
    }
}
