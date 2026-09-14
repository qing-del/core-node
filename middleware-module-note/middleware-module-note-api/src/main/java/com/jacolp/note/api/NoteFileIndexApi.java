package com.jacolp.note.api;

import com.jacolp.note.api.model.NoteFileIndexSource;
import java.util.List;
import java.util.Optional;

/** 向资源索引投影暴露笔记的最小当前事实读模型。 */
public interface NoteFileIndexApi {

    /** 按笔记主键游标读取索引源，包含已软删除记录。 */
    List<NoteFileIndexSource> listAfterId(long afterId, int limit);

    /** 按 ID 读取索引源；硬删除或不存在时返回空。 */
    Optional<NoteFileIndexSource> findById(long noteId);
}
