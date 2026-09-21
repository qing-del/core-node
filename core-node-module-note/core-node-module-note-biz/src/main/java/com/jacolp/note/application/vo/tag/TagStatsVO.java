package com.jacolp.note.application.vo.tag;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User tag statistics")
public class TagStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Total tag count of current user")
    private Long tagCount;

    @Schema(description = "Passed tag count of current user")
    private Long passedCount;
}
