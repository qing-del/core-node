package com.jacolp.audit.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaAuditRecordDO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long applicantUserId;
    private Short applyType;
    private Long targetId;
    private String applyReason;
    private Short status;
    private Long reviewerUserId;
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime reviewTime;
    private LocalDateTime updateTime;
}
