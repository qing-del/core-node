package com.jacolp.document.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Repository;

/**
 * 以二进制安全方式保存文档 Room 元数据和待持久化的 Yjs 更新。
 *
 * <p>这里刻意不使用 {@code StringRedisTemplate}：Yjs 更新是任意二进制数据，
 * 写入 Redis Stream 时必须保持字节完全不变。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentRedisRepository {

    private static final byte[] LINK_APPEND_SCRIPT = loadLinkAppendScript();
    private static final byte[] FIELD_DOCUMENT_ID = bytes("documentId");
    private static final byte[] FIELD_OWNER_USER_ID = bytes("ownerUserId");
    private static final byte[] FIELD_LEGACY_TEAM_ID = bytes("teamId");
    private static final byte[] FIELD_IS_CLOSE = bytes("isClose");
    private static final byte[] FIELD_CLOSE_TOKEN = bytes("closeToken");
    private static final byte[] FIELD_LAST_MODIFY_TIME = bytes("lastModifyTime");
    private static final byte[] FIELD_LAST_MODIFY_USER_ID = bytes("lastModifyUserId");
    private static final byte[] FIELD_UPDATE = bytes("update");
    private static final byte[] FIELD_CLIENT_UPDATE_ID = bytes("clientUpdateId");
    private static final byte[] FIELD_OPERATOR_ID = bytes("operatorId");
    private static final byte[] FIELD_OPERATOR_TYPE = bytes("operatorType");
    private static final byte[] FIELD_CREATED_AT = bytes("createdAt");
    private static final byte[] FIELD_ENVELOPE = bytes("envelope");

    private final RedisConnectionFactory redisConnectionFactory;

    /** 保存原始 Redis 连接工厂，所有读写都在短生命周期连接中完成。 */
    public DocumentRedisRepository(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /** 以 Hash 保存 Room 元数据，并清除本次模型中不存在的可选字段。 */
    public void saveRoomMeta(DocumentRoomMeta meta) {
        // Hash 字段只保存运行态和审计信息，绝不把 Yjs 正文写入 Room Meta。
        Map<byte[], byte[]> fields = new LinkedHashMap<>();
        fields.put(FIELD_DOCUMENT_ID, bytes(meta.documentId()));
        fields.put(FIELD_OWNER_USER_ID, bytes(meta.ownerUserId()));
        fields.put(FIELD_IS_CLOSE, bytes(meta.closeRequested() ? 1 : 0));
        fields.put(FIELD_LAST_MODIFY_TIME, bytes(meta.lastModifyTime()));
        if (meta.lastModifyUserId() != null) {
            // 审计用户存在时写入字段；缺失时不写入，下面会同步删除旧值。
            fields.put(FIELD_LAST_MODIFY_USER_ID, bytes(meta.lastModifyUserId()));
        }
        if (meta.closeToken() != null) {
            // 关闭令牌只在关闭窗口中存在，正常 Room 不保留旧令牌。
            fields.put(FIELD_CLOSE_TOKEN, bytes(meta.closeToken()));
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            byte[] key = bytes(roomMetaKey(meta.documentId()));
            connection.hMSet(key, fields);
            if (meta.lastModifyUserId() == null) {
                // Hash 更新不会自动删除缺失字段，必须显式清理旧审计用户，避免读取到过期信息。
                connection.hDel(key, FIELD_LAST_MODIFY_USER_ID);
            }
            if (meta.closeToken() == null) {
                // 清除已结束或被 reopen 取代的关闭令牌，防止旧 CLOSE 消息误命中。
                connection.hDel(key, FIELD_CLOSE_TOKEN);
            }
            // 新格式已经写入所有者字段，清理旧版本的个人空间字段，避免两个值长期分叉。
            connection.hDel(key, FIELD_LEGACY_TEAM_ID);
        }
    }

    /** 读取一个 Room 的元数据；Redis 中没有该 Hash 时返回空结果。 */
    public Optional<DocumentRoomMeta> findRoomMeta(long documentId) {
        requirePositive(documentId, "documentId");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Map<byte[], byte[]> rawFields = connection.hGetAll(bytes(roomMetaKey(documentId)));
            if (rawFields == null || rawFields.isEmpty()) {
                // Redis 没有 Room Meta 时表示文档尚未进入运行态或已完成最终清理。
                return Optional.empty();
            }
            return Optional.of(toRoomMeta(rawFields));
        }
    }

    /** 追加一条二进制更新，并在 XADD 成功后返回对应的 Redis Stream ID。 */
    public String appendPendingUpdate(DocumentPendingUpdate update) {
        // updateData 直接以 byte[] 写入 Stream，避免 UTF-8 或 JSON 转换破坏 Yjs 字节。
        Map<byte[], byte[]> fields = new LinkedHashMap<>();
        fields.put(FIELD_UPDATE, update.updateData());
        fields.put(FIELD_CLIENT_UPDATE_ID, bytes(update.clientUpdateId()));
        if (update.operatorId() != null) {
            // 系统操作允许没有用户 ID；有认证用户时才写入可选审计字段。
            fields.put(FIELD_OPERATOR_ID, bytes(update.operatorId()));
        }
        fields.put(FIELD_OPERATOR_TYPE, bytes(update.operatorType()));
        fields.put(FIELD_CREATED_AT, bytes(update.createdAt()));

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            RecordId recordId = connection.xAdd(bytes(pendingUpdatesKey(update.documentId())), fields);
            if (recordId == null || recordId.getValue() == null || recordId.getValue().isBlank()) {
                // 没有 Stream ID 就无法确认、删除或幂等转存这条更新，必须视为写入失败。
                throw new IllegalStateException("Redis XADD did not return a stream entry ID");
            }
            return recordId.getValue();
        }
    }

    /** 使用一次 Lua EVAL 原子写入 updates Stream 和 Binding Stream，并返回两个 Stream ID。 */
    public DocumentLinkAcceptedRedisOperations appendLinkPendingUpdate(DocumentPendingUpdate update,
                                                                       DocumentPendingBinding binding) {
        if (update == null || binding == null) {
            throw new IllegalArgumentException("LINK pending update and binding must not be null");
        }
        if (update.documentId() != binding.documentId()) {
            throw new IllegalArgumentException("LINK update and binding document IDs must match");
        }
        List<byte[]> arguments = List.of(
                update.updateData(),
                bytes(update.clientUpdateId()),
                update.operatorId() == null ? bytes("") : bytes(update.operatorId()),
                bytes(update.operatorType()),
                bytes(update.createdAt()),
                binding.envelopeData());
        byte[][] keysAndArguments = new byte[2 + arguments.size()][];
        keysAndArguments[0] = bytes(pendingUpdatesKey(update.documentId()));
        keysAndArguments[1] = bytes(pendingBindingsKey(binding.documentId()));
        for (int index = 0; index < arguments.size(); index++) {
            keysAndArguments[index + 2] = arguments.get(index);
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) connection.eval(LINK_APPEND_SCRIPT, ReturnType.MULTI, 2,
                    keysAndArguments);
            if (result == null || result.size() != 2) {
                throw new IllegalStateException("Redis LINK append did not return two Stream IDs");
            }
            return new DocumentLinkAcceptedRedisOperations(
                    redisResultString(result.get(0), "updates Stream"),
                    redisResultString(result.get(1), "binding Stream"));
        }
    }

    /** 按 Stream 顺序读取指定数量的待刷盘更新，并为每条记录保留其 Redis ID。 */
    public List<StoredDocumentPendingUpdate> readPendingUpdates(long documentId, int maxCount) {
        requirePositive(documentId, "documentId");
        if (maxCount <= 0) {
            // 读取上限必须为正数，否则调用方无法区分“没有数据”和“请求没有读取范围”。
            throw new IllegalArgumentException("maxCount must be positive");
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            List<ByteRecord> records = connection.xRange(
                    bytes(pendingUpdatesKey(documentId)), Range.unbounded(), Limit.limit().count(maxCount));
            if (records == null || records.isEmpty()) {
                // 空 Stream 是正常状态，表示当前没有等待 FLUSH_LOG 的更新。
                return List.of();
            }
            List<StoredDocumentPendingUpdate> updates = new ArrayList<>(records.size());
            for (ByteRecord record : records) {
                updates.add(toStoredPendingUpdate(documentId, record));
            }
            return List.copyOf(updates);
        }
    }

    /** 按 Binding Stream 顺序读取指定数量的待投影 Envelope。 */
    public List<StoredDocumentPendingBinding> readPendingBindings(long documentId, int maxCount) {
        requirePositive(documentId, "documentId");
        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount must be positive");
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            List<ByteRecord> records = connection.xRange(
                    bytes(pendingBindingsKey(documentId)), Range.unbounded(), Limit.limit().count(maxCount));
            if (records == null || records.isEmpty()) {
                return List.of();
            }
            List<StoredDocumentPendingBinding> bindings = new ArrayList<>(records.size());
            for (ByteRecord record : records) {
                bindings.add(toStoredPendingBinding(documentId, record));
            }
            return List.copyOf(bindings);
        }
    }

    /** 删除已成功持久化的 Stream 条目；空 ID 集合不触碰 Redis。 */
    public long deletePendingUpdates(long documentId, Collection<String> redisOpIds) {
        requirePositive(documentId, "documentId");
        if (redisOpIds == null || redisOpIds.isEmpty()) {
            // 没有成功落库的 Redis ID 时无需执行 XDEL，避免无意义地访问 Stream。
            return 0;
        }
        RecordId[] recordIds = redisOpIds.stream().map(DocumentRedisRepository::toRecordId)
                .toArray(RecordId[]::new);
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Long deleted = connection.xDel(bytes(pendingUpdatesKey(documentId)), recordIds);
            return deleted == null ? 0 : deleted;
        }
    }

    /** 删除已成功写入关系投影的 Binding Stream 条目。 */
    public long deletePendingBindings(long documentId, Collection<String> redisOpIds) {
        requirePositive(documentId, "documentId");
        if (redisOpIds == null || redisOpIds.isEmpty()) {
            return 0;
        }
        RecordId[] recordIds = redisOpIds.stream().map(DocumentRedisRepository::toRecordId)
                .toArray(RecordId[]::new);
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Long deleted = connection.xDel(bytes(pendingBindingsKey(documentId)), recordIds);
            return deleted == null ? 0 : deleted;
        }
    }

    /** 返回一个文档当前尚未落库的 Redis Stream 条目数。 */
    public long pendingUpdateCount(long documentId) {
        requirePositive(documentId, "documentId");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Long count = connection.xLen(bytes(pendingUpdatesKey(documentId)));
            return count == null ? 0L : count;
        }
    }

    /** 返回一个文档当前尚未投影的 Binding Stream 条目数。 */
    public long pendingBindingCount(long documentId) {
        requirePositive(documentId, "documentId");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Long count = connection.xLen(bytes(pendingBindingsKey(documentId)));
            return count == null ? 0L : count;
        }
    }

    /** 扫描恢复调度器使用的小型活跃 Room 键空间。 */
    public List<DocumentRoomMeta> findRoomMetas() {
        List<DocumentRoomMeta> metas = new ArrayList<>();
        try (RedisConnection connection = redisConnectionFactory.getConnection();
             Cursor<byte[]> keys = connection.scan(ScanOptions.scanOptions().match("document:meta:*").count(100).build())) {
            while (keys.hasNext()) {
                Map<byte[], byte[]> fields = connection.hGetAll(keys.next());
                if (fields != null && !fields.isEmpty()) {
                    // 扫描可能遇到已被并发删除的 key，只恢复仍有内容的 Hash。
                    metas.add(toRoomMeta(fields));
                }
            }
        }
        return List.copyOf(metas);
    }

    /** 创建或续期跨实例可见的临时会话在线租约。 */
    public void savePresence(String presenceKey, long ttlMs) {
        if (presenceKey == null || presenceKey.isBlank()) {
            // 空 key 无法代表会话，继续写入会污染 presence 统计并掩盖调用方错误。
            throw new IllegalArgumentException("presenceKey must not be blank");
        }
        if (ttlMs <= 0) {
            // presence 必须是会过期的租约，非正 TTL 会让跨实例在线状态失去安全边界。
            throw new IllegalArgumentException("ttlMs must be positive");
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.set(bytes(presenceKey), bytes("1"), Expiration.milliseconds(ttlMs), SetOption.UPSERT);
        }
    }

    /** 主动删除一个会话的 presence 租约；过期租约无需额外清理。 */
    public void deletePresence(String presenceKey) {
        if (presenceKey == null || presenceKey.isBlank()) {
            // 清理路径允许接收已不存在的 key；空值直接视为幂等 no-op。
            return;
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.del(bytes(presenceKey));
        }
    }

    /** 统计所有 core 实例上一个文档尚未过期的会话在线租约。 */
    public long countPresence(long documentId) {
        requirePositive(documentId, "documentId");
        long count = 0L;
        try (RedisConnection connection = redisConnectionFactory.getConnection();
             Cursor<byte[]> keys = connection.scan(ScanOptions.scanOptions()
                     .match("document:presence:" + documentId + ":*").count(100).build())) {
            while (keys.hasNext()) {
                keys.next();
                count++;
            }
        }
        return count;
    }

    /** 最终持久化后只清除 Redis 运行时状态；会话在线租约需要单独校验。 */
    public void deleteRoomRuntime(long documentId) {
        requirePositive(documentId, "documentId");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.del(bytes(roomMetaKey(documentId)), bytes(pendingUpdatesKey(documentId)),
                    bytes(pendingBindingsKey(documentId)));
        }
    }

    /** 生成文档 Room Meta 的稳定 Redis key。 */
    static String roomMetaKey(long documentId) {
        requirePositive(documentId, "documentId");
        return "document:meta:" + documentId;
    }

    /** 生成文档待刷盘 Stream 的稳定 Redis key。 */
    static String pendingUpdatesKey(long documentId) {
        requirePositive(documentId, "documentId");
        return "document:updates:" + documentId;
    }

    /** 生成文档 Binding Stream 的稳定 Redis key。 */
    static String pendingBindingsKey(long documentId) {
        requirePositive(documentId, "documentId");
        return "document:bindings:" + documentId;
    }

    /** 将 Redis Hash 字段恢复为经过领域校验的 Room 元数据。 */
    private static DocumentRoomMeta toRoomMeta(Map<byte[], byte[]> rawFields) {
        Map<String, byte[]> fields = stringFields(rawFields);
        return new DocumentRoomMeta(
                parseRequiredLong(fields, "documentId"),
                parseOwnerUserId(fields),
                parseCloseRequested(fields),
                parseOptionalString(fields, "closeToken"),
                parseRequiredLong(fields, "lastModifyTime"),
                parseOptionalLong(fields, "lastModifyUserId"));
    }

    /** 读取新 ownerUserId；存量 Room Meta 只有旧 teamId 时兼容回退。 */
    private static long parseOwnerUserId(Map<String, byte[]> fields) {
        String ownerUserId = parseOptionalString(fields, "ownerUserId");
        if (ownerUserId != null && !ownerUserId.isBlank()) {
            try {
                return Long.parseLong(ownerUserId);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Redis field ownerUserId must be a long", exception);
            }
        }
        return parseRequiredLong(fields, "teamId");
    }

    /** 将 Redis Stream 记录恢复为带 Stream ID 的待刷盘更新。 */
    private static StoredDocumentPendingUpdate toStoredPendingUpdate(long documentId, ByteRecord record) {
        if (record.getId() == null || record.getId().getValue() == null || record.getId().getValue().isBlank()) {
            // Stream ID 是后续 XDEL 和 MySQL 幂等键的依据，缺失时不能恢复该记录。
            throw new IllegalStateException("Redis Stream record is missing its ID");
        }
        Map<String, byte[]> fields = stringFields(record.getValue());
        byte[] updateData = fields.get("update");
        if (updateData == null || updateData.length == 0) {
            // 空或缺失的 Yjs 更新无法合并，跳过会导致位点推进后永久丢失数据，因此直接报错。
            throw new IllegalStateException("Redis Stream record is missing a binary update");
        }
        return new StoredDocumentPendingUpdate(record.getId().getValue(), new DocumentPendingUpdate(
                documentId,
                updateData,
                requiredString(fields, "clientUpdateId"),
                parseOptionalLong(fields, "operatorId"),
                requiredString(fields, "operatorType"),
                parseRequiredLong(fields, "createdAt")));
    }

    /** 将 Binding Stream 记录恢复为带 Stream ID 的 Envelope。 */
    private static StoredDocumentPendingBinding toStoredPendingBinding(long documentId, ByteRecord record) {
        if (record.getId() == null || record.getId().getValue() == null || record.getId().getValue().isBlank()) {
            throw new IllegalStateException("Redis Binding Stream record is missing its ID");
        }
        Map<String, byte[]> fields = stringFields(record.getValue());
        byte[] envelope = fields.get("envelope");
        if (envelope == null || envelope.length == 0) {
            throw new IllegalStateException("Redis Binding Stream record is missing a binary envelope");
        }
        return new StoredDocumentPendingBinding(record.getId().getValue(),
                new DocumentPendingBinding(documentId, envelope));
    }

    /** 仅把字段名转成字符串，保留字段值的原始字节。 */
    private static Map<String, byte[]> stringFields(Map<byte[], byte[]> rawFields) {
        Map<String, byte[]> fields = new LinkedHashMap<>();
        rawFields.forEach((key, value) -> fields.put(string(key), value));
        return fields;
    }

    /** 将 Redis 中的 0/1 关闭标志解析为布尔值。 */
    private static boolean parseCloseRequested(Map<String, byte[]> fields) {
        String value = requiredString(fields, "isClose");
        return switch (value) {
            case "0" -> false;
            case "1" -> true;
            default -> throw new IllegalStateException("Redis room meta has an invalid isClose value");
        };
    }

    /** 读取并解析必填 long 字段。 */
    private static long parseRequiredLong(Map<String, byte[]> fields, String fieldName) {
        try {
            return Long.parseLong(requiredString(fields, fieldName));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Redis field " + fieldName + " must be a long", exception);
        }
    }

    /** 读取可选 long 字段；字段缺失时保留 null。 */
    private static Long parseOptionalLong(Map<String, byte[]> fields, String fieldName) {
        String value = parseOptionalString(fields, fieldName);
        if (value == null) {
            // 可选字段缺失表示“没有该审计信息”，不能用 0 冒充一个真实用户 ID。
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Redis field " + fieldName + " must be a long", exception);
        }
    }

    /** 读取非空字符串字段，缺失或空白时拒绝损坏数据。 */
    private static String requiredString(Map<String, byte[]> fields, String fieldName) {
        String value = parseOptionalString(fields, fieldName);
        if (value == null || value.isBlank()) {
            // 必填字段损坏时拒绝整条记录，避免将不完整数据继续传入刷盘或 bootstrap。
            throw new IllegalStateException("Redis field " + fieldName + " is required");
        }
        return value;
    }

    /** 读取可选字符串字段，不对缺失字段进行默认填充。 */
    private static String parseOptionalString(Map<String, byte[]> fields, String fieldName) {
        byte[] value = fields.get(fieldName);
        return value == null ? null : string(value);
    }

    /** 将 Stream ID 字符串转换为 Redis 连接 API 所需的 RecordId。 */
    private static RecordId toRecordId(String value) {
        if (value == null || value.isBlank()) {
            // XDEL 必须使用真实 Stream ID；空值不能被转换为安全的删除目标。
            throw new IllegalArgumentException("redisOpId must not be blank");
        }
        return RecordId.of(value);
    }

    /** 校验 Redis key 相关的正数 ID。 */
    private static void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            // 文档 ID 会拼入 Redis key，非正数会把不同调用方错误地映射到无效空间。
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    /** 将标量按 UTF-8 编码，供 Redis key 和非正文字段使用。 */
    private static byte[] bytes(Object value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    /** 将 Redis 非正文字段按 UTF-8 解码。 */
    private static String string(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /** 将 Lua MULTI 返回的 Redis bulk string安全转换为 Stream ID。 */
    private static String redisResultString(Object value, String fieldName) {
        String result;
        if (value instanceof byte[] bytes) {
            result = string(bytes);
        } else if (value instanceof String text) {
            result = text;
        } else {
            result = null;
        }
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("Redis LINK append returned an invalid " + fieldName + " ID");
        }
        return result;
    }

    /** 从 classpath 加载原子双 Stream 入队脚本；脚本缺失属于启动期配置错误。 */
    private static byte[] loadLinkAppendScript() {
        try (var input = new ClassPathResource("lua/document_link_append.lua").getInputStream()) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
