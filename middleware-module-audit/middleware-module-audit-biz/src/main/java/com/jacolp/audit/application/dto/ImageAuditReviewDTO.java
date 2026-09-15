package com.jacolp.audit.application.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageAuditReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long auditId;
    private Boolean approved;
    private String rejectReason;
}
