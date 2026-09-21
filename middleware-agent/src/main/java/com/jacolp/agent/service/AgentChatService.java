package com.jacolp.agent.service;

import com.jacolp.agent.domain.vo.AgentChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Executes stateless, text-only chat requests. */
@Service
public class AgentChatService {

    private final ChatClient agentChatClient;

    public AgentChatService(ChatClient agentChatClient) {
        this.agentChatClient = agentChatClient;
    }

    public AgentChatResponse chat(String message) {
        String content = agentChatClient.prompt()
                .user(message)
                .call()
                .content();
        return new AgentChatResponse(content);
    }
}
