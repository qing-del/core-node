package com.jacolp.audit.application.vo;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageAuditVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long applicantUserId;
    private String applicantUsername;
    private Long imageId;
    private String filename;
    private String ossUrl;
    private String applyReason;
    private Short status;
    private Long reviewerUserId;
    private String reviewerUsername;
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime reviewTime;
    private LocalDateTime updateTime;
}
