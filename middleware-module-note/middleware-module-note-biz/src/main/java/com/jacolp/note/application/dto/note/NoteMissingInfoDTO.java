package com.jacolp.note.application.dto.note;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteMissingInfoDTO {
    private int missingMask;
    private int missingCount;
}
