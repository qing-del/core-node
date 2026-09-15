package com.jacolp.note.application.dto.tag;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagBatchAddDTO implements Serializable {

    @NotEmpty(message = "标签名称不能为空")
    private List<String> tagNames;
}
