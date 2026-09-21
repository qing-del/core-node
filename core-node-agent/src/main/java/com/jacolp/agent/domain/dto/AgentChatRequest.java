package com.jacolp.agent.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** A stateless, single-turn chat request. */
@Schema(description = "Agent chat request")
public record AgentChatRequest(
        @NotBlank(message = "message 不能为空")
        @Schema(description = "用户消息", requiredMode = Schema.RequiredMode.REQUIRED, example = "解释一下 Spring AI")
        String message) {
}
