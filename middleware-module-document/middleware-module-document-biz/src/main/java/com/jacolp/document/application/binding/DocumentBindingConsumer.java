package com.jacolp.document.application.binding;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentRelationDO;
import com.jacolp.document.infrastructure.persistence.dataobject.ResourceNodeDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentRelationMapper;
import com.jacolp.document.infrastructure.persistence.mapper.ResourceNodeMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingBinding;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingBinding;
import com.jacolp.document.websocket.protocol.DocumentBindingCommandType;
import com.jacolp.document.websocket.protocol.DocumentBindingEnvelope;
import com.jacolp.document.websocket.protocol.DocumentBindingTargetType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 单例、非 Consumer Group 的 Binding Stream 投影消费者。
 *
 * <p>消费顺序由 Redis Stream ID 决定；批次内同一 sourceDocumentId/refId 只保留最后一条命令。
 * 关系事务提交后才 XDEL，因此事务失败会留下原消息交给现有调度重试。</p>
 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentBindingConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentBindingConsumer.class);

    private final DocumentRedisRepository documentRedisRepository;
    private final ResourceNodeMapper resourceNodeMapper;
    private final DocumentRelationMapper documentRelationMapper;
    private final TransactionTemplate transactionTemplate;
    private final DocumentProperties properties;

    /** 创建单例 Binding 投影消费者；调度和 CLOSE 复用同一个实例。 */
    public DocumentBindingConsumer(DocumentRedisRepository documentRedisRepository,
                                   ResourceNodeMapper resourceNodeMapper,
                                   DocumentRelationMapper documentRelationMapper,
                                   TransactionTemplate transactionTemplate,
                                   DocumentProperties properties) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository,
                "documentRedisRepository must not be null");
        this.resourceNodeMapper = Objects.requireNonNull(resourceNodeMapper, "resourceNodeMapper must not be null");
        this.documentRelationMapper = Objects.requireNonNull(documentRelationMapper,
                "documentRelationMapper must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 持续排空指定文档的 Binding Stream，直到本次观察范围没有剩余消息。 */
    public synchronized DocumentBindingConsumeResult drain(long documentId) {
        requirePositive(documentId);
        int batchSize = Math.max(1, properties.getFlushLog().getBatchSize());
        int processedCount = 0;
        long deletedCount = 0L;
        while (true) {
            List<StoredDocumentPendingBinding> pending = documentRedisRepository.readPendingBindings(documentId, batchSize);
            if (pending == null || pending.isEmpty()) {
                return processedCount == 0
                        ? DocumentBindingConsumeResult.empty(documentId)
                        : new DocumentBindingConsumeResult(documentId, processedCount, deletedCount);
            }

            List<String> redisOpIds = pending.stream().map(StoredDocumentPendingBinding::redisOpId).toList();
            Map<UUID, DocumentBindingEnvelope> latestByRefId = foldLatest(pending);
            // 所有节点/关系写入必须在同一个事务中完成；这里抛出的异常会阻止后续 XDEL。
            transactionTemplate.execute(status -> {
                for (DocumentBindingEnvelope envelope : latestByRefId.values()) {
                    apply(documentId, envelope);
                }
                return null;
            });
            long deleted = documentRedisRepository.deletePendingBindings(documentId, redisOpIds);
            processedCount += pending.size();
            deletedCount += deleted;
        }
    }

    /** 按 Stream 返回顺序折叠批次，后出现的同一 refId 覆盖前一条命令。 */
    private static Map<UUID, DocumentBindingEnvelope> foldLatest(List<StoredDocumentPendingBinding> pending) {
        Map<UUID, DocumentBindingEnvelope> latestByRefId = new LinkedHashMap<>();
        for (StoredDocumentPendingBinding stored : pending) {
            DocumentPendingBinding binding = stored.binding();
            DocumentBindingEnvelope envelope = DocumentBindingEnvelope.decodeBody(binding.envelopeData());
            latestByRefId.put(envelope.refId(), envelope);
        }
        return latestByRefId;
    }

    /** 在当前关系事务内应用单条已折叠命令。 */
    private void apply(long sourceDocumentId, DocumentBindingEnvelope envelope) {
        if (envelope.targetType() != DocumentBindingTargetType.DOCUMENT) {
            // 当前协议构造器已经拒绝未知类型；保留显式分支避免未来扩展后静默写错 resource_type。
            throw new IllegalStateException("unsupported document LINK target type: " + envelope.targetType());
        }
        if (envelope.commandType() == DocumentBindingCommandType.BIND) {
            applyBind(sourceDocumentId, envelope);
        } else {
            applyUnbind(sourceDocumentId, envelope);
        }
    }

    /** BIND 新建关系，或复用既有 refId 对应的资源节点并恢复软删除关系。 */
    private void applyBind(long sourceDocumentId, DocumentBindingEnvelope envelope) {
        String refId = envelope.refId().toString();
        DocumentRelationDO existing = documentRelationMapper.selectBySourceDocumentIdAndRefId(sourceDocumentId, refId);
        if (existing == null) {
            ResourceNodeDO resourceNode = new ResourceNodeDO(null, envelope.targetType().resourceType(), envelope.targetId());
            int nodeInserted = resourceNodeMapper.insert(resourceNode);
            if (nodeInserted != 1 || resourceNode.getId() == null) {
                throw new IllegalStateException("could not create resource node for document LINK refId=" + refId);
            }
            int relationInserted = documentRelationMapper.insert(
                    new DocumentRelationDO(sourceDocumentId, refId, resourceNode.getId(), false));
            if (relationInserted != 1) {
                throw new IllegalStateException("could not create document relation for refId=" + refId);
            }
            return;
        }

        if (existing.getResourceNodeId() == null) {
            throw new IllegalStateException("document relation has no resource node for refId=" + refId);
        }
        int nodeUpdated = resourceNodeMapper.updateTargetById(existing.getResourceNodeId(),
                envelope.targetType().resourceType(), envelope.targetId());
        if (nodeUpdated != 1) {
            throw new IllegalStateException("could not update resource node for document LINK refId=" + refId);
        }
        int relationActivated = documentRelationMapper.activateBySourceDocumentIdAndRefId(sourceDocumentId, refId,
                existing.getResourceNodeId());
        if (relationActivated != 1) {
            throw new IllegalStateException("could not activate document relation for refId=" + refId);
        }
    }

    /** UNBIND 只软删除已存在的有效关系；不存在或已删除关系按幂等成功处理。 */
    private void applyUnbind(long sourceDocumentId, DocumentBindingEnvelope envelope) {
        String refId = envelope.refId().toString();
        DocumentRelationDO existing = documentRelationMapper.selectBySourceDocumentIdAndRefId(sourceDocumentId, refId);
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            log.info("Ignore idempotent document LINK UNBIND without active relation: documentId={}, refId={}",
                    sourceDocumentId, refId);
            return;
        }
        int deleted = documentRelationMapper.softDeleteBySourceDocumentIdAndRefId(sourceDocumentId, refId);
        if (deleted != 1) {
            throw new IllegalStateException("could not soft-delete document relation for refId=" + refId);
        }
    }

    private static void requirePositive(long documentId) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
    }
}
