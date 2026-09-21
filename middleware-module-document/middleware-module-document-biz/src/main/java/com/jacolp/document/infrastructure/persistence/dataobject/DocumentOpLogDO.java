package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 从 Redis 待持久化 Stream 接收并写入数据库的一条 Yjs 更新。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentOpLogDO implements Serializable {

    /** Java 序列化版本号，不对应数据库字段。<p>example: {@code 1L}</p> */
    private static final long serialVersionUID = 1L;

    /** 数据库自增的持久化操作日志 ID。<p>example: {@code 128}</p> */
    private Long id;
    /** 产生该更新的文档 ID。<p>example: {@code 42}</p> */
    private Long documentId;
    /** Redis Stream 条目 ID，用于幂等去重和删除已刷盘记录。<p>example: {@code 1756080000000-0}</p> */
    private String redisOpId;
    /** 客户端生成的 Yjs 更新 UUID。<p>example: {@code 550e8400-e29b-41d4-a716-446655440000}</p> */
    private String clientUpdateId;
    /** 未经文本转换的 Yjs 二进制增量。<p>example: {@code [0x01, 0x02, 0x7f]}</p> */
    private byte[] updateData;
    /** 执行该更新的用户 ID；系统任务写入时为空。<p>example: {@code 10001}</p> */
    private Long operatorId;
    /** 操作主体类型，用于区分用户、系统恢复等来源。<p>example: {@code USER}</p> */
    private String operatorType;
    /** Redis 接受该更新的服务端时间。<p>example: {@code 2026-08-25T10:30:00}</p> */
    private LocalDateTime createTime;
}
