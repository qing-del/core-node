package com.jacolp.agent.controller.user;

import com.jacolp.agent.domain.dto.AgentChatRequest;
import com.jacolp.agent.domain.vo.AgentChatResponse;
import com.jacolp.agent.service.AgentChatService;
import com.jacolp.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-facing entry point for basic Agent chat. */
@RestController("User-AgentChatController")
@RequestMapping("/user/agent")
@Tag(name = "User-Agent", description = "用户端基础 AI 聊天接口")
@Schema(description = "User - Agent chat")
public class AgentChatController {

    private final AgentChatService agentChatService;

    public AgentChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @PostMapping("/chat")
    @Operation(summary = "基础 AI 聊天", description = "发起一次无历史、无工具的同步文本聊天。")
    public Result<AgentChatResponse> chat(@RequestBody @Valid AgentChatRequest request) {
        return Result.success(agentChatService.chat(request.message()));
    }
}
