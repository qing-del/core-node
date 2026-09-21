package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.jacolp.document.enums.DocumentPermission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code biz_document_user} 文档直接用户授权表的一行持久化模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUserMappingDO implements Serializable {

    /** Java 序列化版本号，不对应数据库字段。 */
    private static final long serialVersionUID = 1L;

    /** 文档 ID；与 userId 组成联合主键。<p>example: {@code 1L}</p> */
    private Long documentId;
    /** 被授权用户 ID；与 documentId 组成联合主键。<p>example: {@code 2L}</p> */
    private Long userId;
    /** 文档权限；WRITE 隐含 READ。<p>example: {@code DocumentPermission.WRITE}</p> */
    private DocumentPermission permission;
    /** 授权是否生效；false 表示已撤销。<p>example: {@code true}</p> */
    private Boolean enabled;
    /** 授权记录创建时间。 */
    private LocalDateTime createTime;
    /** 授权记录最近更新时间。 */
    private LocalDateTime updateTime;
}
