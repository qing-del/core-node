package com.jacolp.document.application.metadata;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.api.model.DocumentMetadata;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 处理文档元数据增删改查；正文 CRDT 状态不经过 HTTP 元数据接口。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentMetadataService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_TITLE_LENGTH = 255;

    private final DocumentMapper documentMapper;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentAccessService documentAccessService;

    /** 兼容旧测试和直接调用方；生产 Bean 使用带 ACL 的构造方法。 */
    public DocumentMetadataService(DocumentMapper documentMapper, DocumentRedisRepository documentRedisRepository) {
        this(documentMapper, documentRedisRepository, null);
    }

    /** 创建带资源级 ACL 判定的元数据服务。 */
    @Autowired
    public DocumentMetadataService(DocumentMapper documentMapper, DocumentRedisRepository documentRedisRepository,
                                   DocumentAccessService documentAccessService) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository,
                "documentRedisRepository must not be null");
        this.documentAccessService = documentAccessService;
    }

    /** 创建所有者为当前用户的文档并返回元数据。 */
    public DocumentMetadata create(long ownerUserId, String title) {
        requireUserId(ownerUserId);
        LocalDateTime now = LocalDateTime.now(APPLICATION_ZONE);
        // 先构造完整的持久化对象，再由 Mapper 回填自增主键，保证创建响应可以立即返回文档 ID。
        DocumentDO document = new DocumentDO(null, ownerUserId, normalizeTitle(title), null, 0L, now,
                ownerUserId, false, 0L, null, null);
        if (documentMapper.insert(document) != 1 || document.getId() == null) {
            // 插入行数或生成的 ID 异常时不能返回半成品元数据，统一转换为创建失败。
            throw new BaseException("创建文档失败");
        }
        return toMetadata(document, ownerAccess(document));
    }

    /** 查询当前用户可见的活跃文档列表，包括所有者和有效授权文档。 */
    public List<DocumentMetadata> list(long userId) {
        requireUserId(userId);
        // Mapper 先筛出所有者或存在有效映射的活跃文档，随后逐条计算最终 ACL 并映射为 DTO。
        return documentMapper.listActiveVisibleByUserId(userId).stream()
                .map(document -> toMetadata(document, accessFor(userId, document))).toList();
    }

    /** 按当前用户的文档 ACL 读取元数据；不存在和无权限统一由访问服务处理。 */
    public DocumentMetadata get(long userId, long documentId) {
        if (documentAccessService == null) {
            // 只有旧的双参数构造路径没有 ACL Bean，此处保留原有所有者范围查询以兼容旧测试。
            DocumentDO document = findRequired(userId, documentId);
            return toMetadata(document, ownerAccess(document));
        }
        // 生产路径由统一访问服务同时处理文档存在性和 READ/WRITE/OWNER 权限。
        return toMetadata(documentAccessService.requireRead(documentId, userId));
    }

    /** 校验并更新文档标题；标题管理仍然只允许所有者。 */
    public DocumentMetadata updateTitle(long ownerUserId, long documentId, String title) {
        requireUserId(ownerUserId);
        requireDocumentId(documentId);
        String normalizedTitle = normalizeTitle(title);
        if (documentAccessService != null) {
            // 标题是文档管理能力，不因协作者拥有 WRITE 正文权限而开放给协作者。
            documentAccessService.requireOwner(documentId, ownerUserId);
        }
        if (documentMapper.updateTitleIfActive(documentId, ownerUserId, normalizedTitle,
                LocalDateTime.now(APPLICATION_ZONE), ownerUserId) != 1) {
            // UPDATE 同时带有 owner/deleted 条件；未更新表示文档不存在、已删除或归属已变化。
            throw notFound();
        }
        // 更新后重新读取文档，返回最新标题、修改时间和所有者权限，而不是复用旧对象快照。
        DocumentDO document = documentAccessService == null
                ? findRequired(ownerUserId, documentId)
                : documentAccessService.requireOwner(documentId, ownerUserId).document();
        return toMetadata(document, ownerAccess(document));
    }

    /** 只软删除没有活跃协作会话的文档。 */
    public void delete(long ownerUserId, long documentId) {
        DocumentDO document = documentAccessService == null
                ? findRequired(ownerUserId, documentId)
                : documentAccessService.requireOwner(documentId, ownerUserId).document();
        if (documentRedisRepository.countPresence(document.getId()) > 0) {
            // 跨实例 presence 仍存在时禁止删除，避免在线协作者继续写入已删除文档。
            throw new BaseException("文档仍有活跃协作会话，暂不能删除");
        }
        if (documentMapper.softDeleteByIdAndOwnerUserId(documentId, ownerUserId,
                LocalDateTime.now(APPLICATION_ZONE), ownerUserId) != 1) {
            // 软删除只接受活跃且归属匹配的文档，受影响行数为零时返回中性资源错误。
            throw notFound();
        }
    }

    /** 旧构造方法的所有者范围查询，仅保留测试/兼容调用路径。 */
    private DocumentDO findRequired(long ownerUserId, long documentId) {
        requireUserId(ownerUserId);
        requireDocumentId(documentId);
        DocumentDO document = documentMapper.selectActiveById(documentId);
        if (document != null && !Objects.equals(document.getOwnerUserId(), ownerUserId)) {
            // 兼容路径仍需在应用层补做 owner 判断，不能把其他用户的文档泄露给旧调用方。
            document = null;
        }
        if (document == null) {
            // 不区分不存在、已删除和非所有者，沿用中性错误消息避免资源枚举。
            throw notFound();
        }
        return document;
    }

    /** 将数据库文档及本次 ACL 结果映射为跨模块元数据 DTO。 */
    private DocumentMetadata toMetadata(DocumentDO document, DocumentAccess access) {
        // 对外只暴露元数据和本次访问结果，不把正文或 Redis/对象存储内部指针带出接口。
        LocalDateTime lastModifyTime = Objects.requireNonNull(document.getLastModifyTime(),
                "active document must have lastModifyTime");
        return new DocumentMetadata(document.getId(), document.getOwnerUserId(), document.getTitle(),
                lastModifyTime.atZone(APPLICATION_ZONE).toInstant().toEpochMilli(), document.getLastModifyUserId(),
                Boolean.TRUE.equals(document.getDeleted()), access.permission().name(), access.owner());
    }

    /** 将统一访问结果直接转换为元数据响应，避免调用方重复拆分 document/access。 */
    private DocumentMetadata toMetadata(DocumentAccess access) {
        return toMetadata(access.document(), access);
    }

    /** 为列表中的每篇文档重新计算当前用户权限，防止列表快照携带过期 ACL。 */
    private DocumentAccess accessFor(long userId, DocumentDO document) {
        return documentAccessService == null ? ownerAccess(document)
                : documentAccessService.requireRead(document.getId(), userId);
    }

    /** 构造所有者的固定访问结果；所有者始终拥有 WRITE 和资源管理能力。 */
    private static DocumentAccess ownerAccess(DocumentDO document) {
        return new DocumentAccess(document, DocumentPermission.WRITE, true);
    }

    /** 规范化标题并限制长度，作为写入数据库和返回 DTO 前的唯一入口。 */
    private static String normalizeTitle(String title) {
        if (title == null) {
            // null 无法产生可读标题，先于 trim 处理以避免空指针。
            throw new BaseException("文档标题不能为空");
        }
        String normalized = title.trim();
        if (normalized.isEmpty()) {
            // 只包含空白的标题在展示上等同于空标题，因此拒绝保存。
            throw new BaseException("文档标题不能为空");
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            // 数据库列上限为 255 个字符，应用层先校验以返回明确业务错误。
            throw new BaseException("文档标题不能超过 255 个字符");
        }
        return normalized;
    }

    /** 校验用户 ID，阻止无效身份进入 Mapper 查询。 */
    private static void requireUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    /** 校验文档 ID，并使用中性错误保持 HTTP/ACL 的资源不可枚举性。 */
    private static void requireDocumentId(long documentId) {
        if (documentId <= 0) {
            throw new BaseException("文档不存在或无权访问");
        }
    }

    /** 创建统一的文档资源错误，供查询、更新和删除共用。 */
    private static BaseException notFound() {
        return new BaseException("文档不存在或无权访问");
    }
}
