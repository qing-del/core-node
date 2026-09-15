package com.jacolp.media.application.dto.image;

import com.jacolp.media.application.dto.PageParamProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserImageQueryDTO implements Serializable, PageParamProvider {
    private static final long serialVersionUID = 1L;
    private Long topicId;
    private String filename;
    private String scope;
    private Integer pageNum;
    private Integer pageSize;
}
