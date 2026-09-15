package com.jacolp.audit.application.dto;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditBatchReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Long> ids;
    private Short status;
    private String rejectReason;
}
