package com.jacolp.audio.persistence.dataobject;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioTaskDO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long userId;
    private String sourceText;
    private BigDecimal speed;
    private String noiseType;
    private BigDecimal noiseFactor;
    private Integer status;
    private Integer retryTime;
    private String resultUrl;
    private Long audioSize;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDate completedDate;
}
