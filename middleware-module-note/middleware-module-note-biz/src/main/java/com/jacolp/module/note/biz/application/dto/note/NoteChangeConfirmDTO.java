package com.jacolp.module.note.biz.application.dto.note;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteChangeConfirmDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    @NotNull(message = "确认参数不能为空")
    private Boolean confirm;
}
