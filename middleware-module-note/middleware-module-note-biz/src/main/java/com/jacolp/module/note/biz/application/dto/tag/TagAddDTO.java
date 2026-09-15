package com.jacolp.module.note.biz.application.dto.tag;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagAddDTO implements Serializable {

    private String tagName;
}
