package com.jacolp.agent.service;

import com.jacolp.agent.domain.dto.AgentChatRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatServiceTest {

    @Test
    void sendsOnlyTheCurrentMessageAndReturnsTheCompleteModelContent() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("你好")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("你好，有什么可以帮你？");

        var response = new AgentChatService(chatClient).chat("你好");

        assertThat(response.content()).isEqualTo("你好，有什么可以帮你？");
        verify(requestSpec).user("你好");
        verify(requestSpec).call();
    }

    @Test
    void rejectsBlankMessages() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new AgentChatRequest(" ")))
                .extracting(violation -> violation.getMessage())
                .containsExactly("message 不能为空");
    }
}
