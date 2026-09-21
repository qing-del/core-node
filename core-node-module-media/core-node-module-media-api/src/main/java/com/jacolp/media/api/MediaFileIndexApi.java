package com.jacolp.media.api;

import com.jacolp.media.api.model.MediaFileIndexSource;
import java.util.List;
import java.util.Optional;

/** 向资源索引投影暴露图片的最小当前事实读模型。 */
public interface MediaFileIndexApi {

    /** 按图片主键游标读取索引源，包含已软删除记录。 */
    List<MediaFileIndexSource> listAfterId(long afterId, int limit);

    /** 按 ID 读取索引源；硬删除或不存在时返回空。 */
    Optional<MediaFileIndexSource> findById(long mediaId);
}
