package com.jacolp.agent.controller.user;

import com.jacolp.agent.domain.vo.AgentChatResponse;
import com.jacolp.agent.service.AgentChatService;
import com.jacolp.common.core.result.Result;
import com.jacolp.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentChatControllerTest {

    @Test
    void returnsTheServiceResponseInTheEstablishedResultEnvelope() throws Exception {
        AgentChatService service = mock(AgentChatService.class);
        when(service.chat("你好")).thenReturn(new AgentChatResponse("你好！"));
        MockMvc mvc = mvc(service);

        mvc.perform(post("/user/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS))
                .andExpect(jsonPath("$.data.content").value("你好！"));
        verify(service).chat("你好");
    }

    @Test
    void rejectsBlankMessagesBeforeCallingTheModel() throws Exception {
        AgentChatService service = mock(AgentChatService.class);
        MockMvc mvc = mvc(service);

        mvc.perform(post("/user/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\" \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.FAIL))
                .andExpect(jsonPath("$.msg").value("message 不能为空"));
        verify(service, never()).chat(anyString());
    }

    private static MockMvc mvc(AgentChatService service) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new AgentChatController(service))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
