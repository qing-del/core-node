package com.jacolp.middleware.module.audio.biz.interfaces.web.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.audio.controller.common.AudioCallbackController;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.audio.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.service.AudioTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioCallbackControllerTest {
    private AudioTaskService audioTaskService;
    private AudioCallbackController controller;

    @BeforeEach
    void setUp() {
        audioTaskService = mock(AudioTaskService.class);
        controller = new AudioCallbackController();
        ReflectionTestUtils.setField(controller, "audioTaskService", audioTaskService);
        ReflectionTestUtils.setField(controller, "callbackToken", "callback-secret");
    }

    @Test
    void validTokenCallsServiceAndReturnsItsResult() {
        AudioCallbackStartDTO dto = new AudioCallbackStartDTO();
        dto.setTaskId(10L);
        dto.setAttempt(0);
        MockHttpServletRequest request = request("callback-secret");
        when(audioTaskService.callbackStart(dto)).thenReturn(true);

        assertThat(controller.callbackStart(dto, request).getData()).isTrue();

        verify(audioTaskService).callbackStart(dto);
    }

    @Test
    void invalidTokenRejectsWithoutCallingService() {
        AudioCallbackStartDTO dto = new AudioCallbackStartDTO();
        dto.setTaskId(10L);
        dto.setAttempt(0);

        assertThatThrownBy(() -> controller.callbackStart(dto, request("wrong-token")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void pythonCallbackAcceptsRequiredAudioSizeFieldName() throws Exception {
        AudioCallbackFinishDTO dto = new ObjectMapper().readValue(
                "{\"taskId\":10,\"attempt\":3,\"status\":2,\"resultUrl\":\"https://audio.example/10.mp3\",\"AudioSize\":512}",
                AudioCallbackFinishDTO.class);

        assertThat(dto.getAudioSize()).isEqualTo(512L);
        assertThat(dto.getAttempt()).isEqualTo(3);
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Callback-Token", token);
        return request;
    }
}
