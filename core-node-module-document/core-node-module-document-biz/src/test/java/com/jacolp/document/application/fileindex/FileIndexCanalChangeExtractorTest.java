package com.jacolp.document.application.fileindex;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.EntryType;
import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileIndexCanalChangeExtractorTest {

    @Test
    void filtersUnrelatedUpdatesAndDeduplicatesResourceKeysInsideOneBatch() {
        Entry relevantNoteUpdate = row("biz_note", EventType.UPDATE,
                List.of(column("id", "7", false), column("title", "新标题", true)));
        Entry unrelatedNoteUpdate = row("biz_note", EventType.UPDATE,
                List.of(column("id", "7", false), column("content", "正文", true)));
        Entry repeatedNoteUpdate = row("biz_note", EventType.UPDATE,
                List.of(column("id", "7", false), column("status", "6", true)));

        List<FileIndexResourceKey> keys = new FileIndexCanalChangeExtractor().extract(List.of(
                relevantNoteUpdate, unrelatedNoteUpdate, repeatedNoteUpdate));

        assertThat(keys).containsExactly(new FileIndexResourceKey(FileIndexResourceType.NOTE, 7L));
    }

    @Test
    void mapsDocumentUserChangesToTheDocumentIdAndKeepsBothIdsWhenKeyMoves() {
        Entry authorizationUpdate = row("biz_document_user", EventType.UPDATE,
                List.of(column("document_id", "9", true), column("user_id", "42", false),
                        column("permission", "WRITE", false)));
        Entry authorizationDelete = newEntry("biz_document_user", EventType.DELETE,
                List.of(column("document_id", "10", false), column("user_id", "42", false)));

        List<FileIndexResourceKey> keys = new FileIndexCanalChangeExtractor().extract(
                List.of(authorizationUpdate, authorizationDelete));

        assertThat(keys).containsExactly(new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, 9L),
                new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, 10L));
    }

    private static Entry row(String table, EventType eventType, List<Column> afterColumns) {
        return newEntry(table, eventType, afterColumns);
    }

    private static Entry newEntry(String table, EventType eventType, List<Column> columns) {
        RowData rowData = eventType == EventType.DELETE
                ? RowData.newBuilder().addAllBeforeColumns(columns).build()
                : RowData.newBuilder().addAllAfterColumns(columns).build();
        CanalEntry.RowChange change = CanalEntry.RowChange.newBuilder().setEventType(eventType)
                .addRowDatas(rowData).build();
        CanalEntry.Header header = CanalEntry.Header.newBuilder().setTableName(table).build();
        return Entry.newBuilder().setEntryType(EntryType.ROWDATA).setHeader(header)
                .setStoreValue(change.toByteString()).build();
    }

    private static Column column(String name, String value, boolean updated) {
        return Column.newBuilder().setName(name).setValue(value).setUpdated(updated).build();
    }
}
