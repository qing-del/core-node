package com.jacolp.middleware.module.audio.biz.interfaces.web.common;

import com.jacolp.audio.biz.controller.common.AudioCallbackController;
import com.jacolp.exception.AuthenticationException;
import com.jacolp.audio.biz.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.biz.service.AudioTaskService;
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
        MockHttpServletRequest request = request("callback-secret");
        when(audioTaskService.callbackStart(dto)).thenReturn(true);

        assertThat(controller.callbackStart(dto, request).getData()).isTrue();

        verify(audioTaskService).callbackStart(dto);
    }

    @Test
    void invalidTokenRejectsWithoutCallingService() {
        AudioCallbackStartDTO dto = new AudioCallbackStartDTO();
        dto.setTaskId(10L);

        assertThatThrownBy(() -> controller.callbackStart(dto, request("wrong-token")))
                .isInstanceOf(AuthenticationException.class);
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Callback-Token", token);
        return request;
    }
}
