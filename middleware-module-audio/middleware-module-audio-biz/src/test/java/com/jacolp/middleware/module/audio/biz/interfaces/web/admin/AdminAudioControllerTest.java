package com.jacolp.middleware.module.audio.biz.interfaces.web.admin;

import com.jacolp.audio.biz.controller.admin.AudioController;
import com.jacolp.audio.biz.domain.vo.AudioTaskStatisticsVO;
import com.jacolp.audio.biz.service.AudioTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAudioControllerTest {

    @Test
    void statisticsEndpointReturnsServiceResult() {
        AudioTaskService service = mock(AudioTaskService.class);
        AudioController controller = new AudioController();
        ReflectionTestUtils.setField(controller, "audioTaskService", service);
        AudioTaskStatisticsVO statistics = new AudioTaskStatisticsVO(8L, 3L, 5L, 2L);
        when(service.getStatistics()).thenReturn(statistics);

        assertThat(controller.getStatistics().getData()).isSameAs(statistics);
        verify(service).getStatistics();
    }
}
