package com.jacolp.note.application.dto.tag;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagModifyDTO implements Serializable {

    private Long id;

    private String tagName;
}
