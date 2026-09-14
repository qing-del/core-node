package com.jacolp.document.application.fileindex;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.Message;
import com.jacolp.document.config.FileIndexCanalProperties;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Canal 增量拉取器：Rabbit 发布确认成功后 ACK，失败则 rollback 当前 batch。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document.file-index.canal", name = "enabled", havingValue = "true")
public class FileIndexCanalPoller {

    private static final Logger log = LoggerFactory.getLogger(FileIndexCanalPoller.class);

    private final FileIndexCanalProperties properties;
    private final FileIndexCanalChangeExtractor changeExtractor;
    private final FileIndexCanalPublisher publisher;
    private final CanalConnector connector;
    private final AtomicBoolean connected = new AtomicBoolean();

    /** 创建真实 Canal Client；连接只在显式开启 Canal 后建立。 */
    @Autowired
    public FileIndexCanalPoller(FileIndexCanalProperties properties,
                                FileIndexCanalChangeExtractor changeExtractor,
                                FileIndexCanalPublisher publisher) {
        this(properties, changeExtractor, publisher, createConnector(properties));
    }

    /** 为单元测试提供可替换 connector，不改变生产构造路径。 */
    FileIndexCanalPoller(FileIndexCanalProperties properties, FileIndexCanalChangeExtractor changeExtractor,
                         FileIndexCanalPublisher publisher, CanalConnector connector) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.changeExtractor = Objects.requireNonNull(changeExtractor, "changeExtractor must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.connector = Objects.requireNonNull(connector, "connector must not be null");
    }

    /** 连接并订阅数据库过滤规则；启动前 rollback 可恢复上次未 ACK 的 batch。 */
    @PostConstruct
    public void connect() {
        if (connected.compareAndSet(false, true)) {
            try {
                connector.connect();
                connector.subscribe(properties.getFilter());
                connector.rollback();
            } catch (RuntimeException failure) {
                connected.set(false);
                throw failure;
            }
        }
    }

    /** 拉取一个 Canal batch，空 batch 不产生 Rabbit 消息但仍不需要 ACK。 */
    @Scheduled(fixedDelayString = "${jacolp.document.file-index.canal.poll-delay-ms:100}")
    public void poll() {
        if (!connected.get()) {
            return;
        }
        Message batch;
        try {
            batch = connector.get(requireBatchSize(properties.getBatchSize()));
        } catch (RuntimeException failure) {
            log.warn("file index Canal get failed: {}", safeMessage(failure));
            return;
        }
        if (batch == null || batch.getId() < 0 || batch.getEntries() == null || batch.getEntries().isEmpty()) {
            return;
        }
        try {
            List<FileIndexResourceKey> keys = changeExtractor.extract(batch);
            publisher.publish(keys);
            // 只有 publisher 的 confirm 成功返回后，才推进 Canal 消费位点。
            connector.ack(batch.getId());
            log.debug("file index Canal batch acknowledged batchId={} resourceCount={}", batch.getId(), keys.size());
        } catch (RuntimeException failure) {
            rollback(batch.getId(), failure);
        }
    }

    /** 断开 Canal 连接，避免应用关闭时留下客户端会话。 */
    @PreDestroy
    public void disconnect() {
        if (connected.compareAndSet(true, false)) {
            connector.disconnect();
        }
    }

    private void rollback(long batchId, RuntimeException failure) {
        try {
            connector.rollback(batchId);
            log.warn("file index Canal batch rolled back batchId={} reason={}", batchId, safeMessage(failure));
        } catch (RuntimeException rollbackFailure) {
            log.error("file index Canal batch rollback failed batchId={} reason={}", batchId,
                    safeMessage(rollbackFailure), rollbackFailure);
        }
    }

    private static CanalConnector createConnector(FileIndexCanalProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        if (properties.getHost() == null || properties.getHost().isBlank()) {
            throw new IllegalArgumentException("jacolp.document.file-index.canal.host must not be blank");
        }
        if (properties.getPort() <= 0 || properties.getPort() > 65_535) {
            throw new IllegalArgumentException("jacolp.document.file-index.canal.port must be between 1 and 65535");
        }
        if (properties.getDestination() == null || properties.getDestination().isBlank()) {
            throw new IllegalArgumentException("jacolp.document.file-index.canal.destination must not be blank");
        }
        return CanalConnectors.newSingleConnector(new InetSocketAddress(properties.getHost(), properties.getPort()),
                properties.getDestination(), nullToEmpty(properties.getUsername()), nullToEmpty(properties.getPassword()));
    }

    private static int requireBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalStateException("jacolp.document.file-index.canal.batch-size must be positive");
        }
        return batchSize;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message;
    }
}
