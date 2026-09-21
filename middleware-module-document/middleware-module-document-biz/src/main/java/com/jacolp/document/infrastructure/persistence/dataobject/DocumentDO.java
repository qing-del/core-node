package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** v0.4 {@code biz_document} 表的一行持久化模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDO implements Serializable {

    /** Java 序列化版本号，不对应数据库字段。<p>example: {@code 1L}</p> */
    private static final long serialVersionUID = 1L;

    /** 数据库自增主键。<p>example: {@code 42}</p> */
    private Long id;
    /** 文档所有者用户 ID。<p>example: {@code 10001}</p> */
    private Long ownerUserId;
    /** 文档标题。<p>example: {@code 项目设计文档}</p> */
    private String title;
    /** 当前 Yjs 快照在 MinIO 中的对象键；尚未生成快照时为空。<p>example: {@code document/42/state/550e8400-e29b-41d4-a716-446655440000.bin}</p> */
    private String contentObjectKey;
    /** 当前快照已包含的最大操作日志 ID。<p>example: {@code 128}</p> */
    private Long persistedLogId;
    /** 最近一次接受文档更新的服务端时间。<p>example: {@code 2026-08-25T10:30:00}</p> */
    private LocalDateTime lastModifyTime;
    /** 最近修改文档的用户 ID；系统恢复等无用户操作时为空。<p>example: {@code 10001}</p> */
    private Long lastModifyUserId;
    /** 逻辑删除标记，{@code false} 表示活跃。<p>example: {@code false}</p> */
    private Boolean deleted;
    /** 文档元数据/快照指针的乐观锁版本。<p>example: {@code 3}</p> */
    private Long version;
    /** 文档创建时间。<p>example: {@code 2026-08-24T09:00:00}</p> */
    private LocalDateTime createTime;
    /** 文档记录最近一次更新的时间。<p>example: {@code 2026-08-25T10:30:01}</p> */
    private LocalDateTime updateTime;
}
