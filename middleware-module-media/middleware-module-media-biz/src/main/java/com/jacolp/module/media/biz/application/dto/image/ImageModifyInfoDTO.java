package com.jacolp.module.media.biz.application.dto.image;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageModifyInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long topicId;
}
