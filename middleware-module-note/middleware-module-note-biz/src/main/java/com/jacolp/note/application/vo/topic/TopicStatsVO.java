package com.jacolp.note.application.vo.topic;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User topic statistics")
public class TopicStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Total topic count of current user")
    private Long topicCount;

    @Schema(description = "Passed topic count of current user")
    private Long passedCount;
}
