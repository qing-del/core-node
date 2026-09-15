package com.jacolp.audit.application.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditReviewContext {
    private List<Long> ids;
    private Short status;
    private Long reviewerUserId;
    private String rejectReason;
}
