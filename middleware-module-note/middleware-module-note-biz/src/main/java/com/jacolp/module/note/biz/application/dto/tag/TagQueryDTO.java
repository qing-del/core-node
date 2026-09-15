package com.jacolp.module.note.biz.application.dto.tag;

import java.io.Serializable;

import com.jacolp.module.note.biz.application.dto.PageParamProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagQueryDTO implements Serializable, PageParamProvider {

    private Long userId;

    private String keyword;

    private Integer pageNum;

    private Integer pageSize;
}
