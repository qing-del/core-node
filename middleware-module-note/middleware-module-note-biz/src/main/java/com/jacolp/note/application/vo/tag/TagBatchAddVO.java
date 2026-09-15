package com.jacolp.note.application.vo.tag;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagBatchAddVO implements Serializable {

    private Integer successCount;

    private List<String> existingTags;
}
