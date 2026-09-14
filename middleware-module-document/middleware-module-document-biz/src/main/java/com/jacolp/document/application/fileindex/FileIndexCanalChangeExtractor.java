package com.jacolp.document.application.fileindex;

import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.EntryType;
import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowChange;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;
import com.alibaba.otter.canal.protocol.Message;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 将 Canal ROWDATA 转换为去重后的资源定位键，过滤无关字段和非业务表。 */
@Component
public class FileIndexCanalChangeExtractor {

    private static final Map<String, TableSpec> TABLES = Map.of(
            "biz_note", new TableSpec(FileIndexResourceType.NOTE, Set.of("title", "status", "user_id"), "id"),
            "biz_image", new TableSpec(FileIndexResourceType.IMAGE,
                    Set.of("filename", "user_id", "is_public", "audit_status", "oss_url"), "id"),
            "biz_document", new TableSpec(FileIndexResourceType.DOCUMENT,
                    Set.of("title", "owner_user_id", "deleted"), "id"),
            "biz_document_user", new TableSpec(FileIndexResourceType.DOCUMENT,
                    Set.of("document_id", "user_id", "permission", "enabled"), "document_id"));

    private static final Set<EventType> ROW_EVENTS = EnumSet.of(EventType.INSERT, EventType.UPDATE, EventType.DELETE);

    /** 从 Canal batch 中提取资源键，并在 batch 内按类型和 ID 去重。 */
    public List<FileIndexResourceKey> extract(Message message) {
        if (message == null || message.getEntries() == null) {
            return List.of();
        }
        return extract(message.getEntries());
    }

    /** 从一批 ROWDATA entry 中提取资源键；事务、DDL 和无关字段不会生成事件。 */
    public List<FileIndexResourceKey> extract(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<FileIndexResourceKey> keys = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (entry == null || entry.getEntryType() != EntryType.ROWDATA) {
                continue;
            }
            RowChange change = parseRowChange(entry);
            TableSpec table = TABLES.get(entry.getHeader().getTableName());
            if (table == null || !ROW_EVENTS.contains(change.getEventType())) {
                continue;
            }
            for (RowData row : change.getRowDatasList()) {
                List<Column> preferred = change.getEventType() == EventType.DELETE
                        ? row.getBeforeColumnsList() : row.getAfterColumnsList();
                List<Column> fallback = change.getEventType() == EventType.DELETE
                        ? row.getAfterColumnsList() : row.getBeforeColumnsList();
                if (change.getEventType() == EventType.UPDATE
                        && !hasUpdatedRelevantColumn(preferred, table.relevantColumns())) {
                    continue;
                }
                if (table.resourceType() == FileIndexResourceType.DOCUMENT
                        && "document_id".equals(table.idColumn())) {
                    addDocumentIds(keys, preferred, fallback);
                } else if (hasRelevantColumn(preferred, table.relevantColumns())) {
                    keys.add(new FileIndexResourceKey(table.resourceType(),
                            parseId(findValue(preferred, fallback, table.idColumn()), table.idColumn())));
                }
            }
        }
        return List.copyOf(keys);
    }

    /** 解析 protobuf RowChange，并把损坏的 binlog entry 交给上层 rollback。 */
    private static RowChange parseRowChange(Entry entry) {
        try {
            return RowChange.parseFrom(entry.getStoreValue());
        } catch (Exception exception) {
            throw new IllegalArgumentException("could not parse Canal row change", exception);
        }
    }

    private static void addDocumentIds(LinkedHashSet<FileIndexResourceKey> keys, List<Column> preferred,
                                       List<Column> fallback) {
        for (String value : values(preferred, "document_id")) {
            keys.add(new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, parseId(value, "document_id")));
        }
        for (String value : values(fallback, "document_id")) {
            keys.add(new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, parseId(value, "document_id")));
        }
    }

    private static boolean hasUpdatedRelevantColumn(List<Column> columns, Set<String> relevantColumns) {
        return columns.stream().anyMatch(column -> column.getUpdated() && relevantColumns.contains(column.getName()));
    }

    private static boolean hasRelevantColumn(List<Column> columns, Set<String> relevantColumns) {
        return columns.stream().anyMatch(column -> relevantColumns.contains(column.getName()));
    }

    private static String findValue(List<Column> preferred, List<Column> fallback, String name) {
        String value = values(preferred, name).stream().findFirst().orElse(null);
        if (value == null || value.isBlank()) {
            value = values(fallback, name).stream().findFirst().orElse(null);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Canal row is missing " + name);
        }
        return value;
    }

    private static List<String> values(List<Column> columns, String name) {
        return columns.stream().filter(column -> name.equals(column.getName()))
                .map(Column::getValue).filter(value -> value != null && !value.isBlank()).toList();
    }

    private static long parseId(String value, String columnName) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Canal " + columnName + " must be a positive integer", exception);
        }
    }

    private record TableSpec(FileIndexResourceType resourceType, Set<String> relevantColumns, String idColumn) {
    }
}
