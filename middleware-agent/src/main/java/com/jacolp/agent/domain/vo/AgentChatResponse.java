package com.jacolp.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** The complete text response for one chat request. */
@Schema(description = "Agent chat response")
public record AgentChatResponse(
        @Schema(description = "模型完整回答", example = "Spring AI 为 Java AI 应用提供统一抽象。")
        String content) {
}
