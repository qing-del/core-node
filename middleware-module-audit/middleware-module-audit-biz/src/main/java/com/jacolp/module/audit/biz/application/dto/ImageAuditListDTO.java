package com.jacolp.module.audit.biz.application.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageAuditListDTO implements Serializable, PageParamProvider {
    private static final long serialVersionUID = 1L;
    private Short status;
    private Long applicantUserId;
    private Integer pageNum;
    private Integer pageSize;
}
