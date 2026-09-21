package com.jacolp.note.application.vo.note;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User note statistics")
public class NoteStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Total note count of current user (not deleted)")
    private Long noteTotalCount;

    @Schema(description = "Published note count of current user")
    private Long publicNoteCount;

    @Schema(description = "Passed note count of current user")
    private Long passedNoteCount;
}
