package com.jacolp.note.application.api;

import com.jacolp.note.api.NoteFileIndexApi;
import com.jacolp.note.api.model.NoteFileIndexSource;
import com.jacolp.note.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 笔记模块拥有的 file 索引源读适配器。 */
@Component
public class NoteFileIndexApiService implements NoteFileIndexApi {

    private static final short NOTE_PUBLIC = 6;
    private static final short NOTE_DELETED = 8;

    private final NoteMapper noteMapper;

    /** 保存笔记 persistence 查询依赖。 */
    public NoteFileIndexApiService(NoteMapper noteMapper) {
        this.noteMapper = Objects.requireNonNull(noteMapper, "noteMapper must not be null");
    }

    /** 按主键游标返回包含软删除状态的索引源。 */
    @Override
    public List<NoteFileIndexSource> listAfterId(long afterId, int limit) {
        requireNonNegative(afterId, "afterId");
        requirePositive(limit, "limit");
        return noteMapper.selectFileIndexPage(afterId, limit).stream().map(NoteFileIndexApiService::toSource).toList();
    }

    /** 读取单个笔记的当前索引源；硬删除后的空记录由调用方解释为删除 ES 文档。 */
    @Override
    public Optional<NoteFileIndexSource> findById(long noteId) {
        requirePositive(noteId, "noteId");
        return Optional.ofNullable(noteMapper.selectFileIndexById(noteId)).map(NoteFileIndexApiService::toSource);
    }

    /** 将笔记状态映射为 file 索引语义，避免状态码泄漏到 document 模块。 */
    private static NoteFileIndexSource toSource(NoteDO note) {
        if (note == null) {
            throw new IllegalArgumentException("note index source must not be null");
        }
        Short status = Objects.requireNonNull(note.getStatus(), "note status must not be null");
        return new NoteFileIndexSource(note.getId(), note.getUserId(), note.getTitle(), status == NOTE_PUBLIC,
                status == NOTE_DELETED);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
