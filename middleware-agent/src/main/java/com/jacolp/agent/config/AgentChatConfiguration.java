package com.jacolp.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the single ChatClient used by the agent module. */
@Configuration(proxyBeanMethods = false)
public class AgentChatConfiguration {

    @Bean
    public ChatClient agentChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}
