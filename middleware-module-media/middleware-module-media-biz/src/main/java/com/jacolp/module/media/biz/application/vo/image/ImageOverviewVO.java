package com.jacolp.module.media.biz.application.vo.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User image statistics")
public class ImageOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long imageCount;
    private Long passedCount;
}
