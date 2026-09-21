package com.jacolp.document.application.yjs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class YjsMergeWireCodecTest {

    @Test
    void shouldEncodeOpaqueBinaryStateAndUpdatesAsBase64() {
        YjsMergeRequest request = YjsMergeRequest.from(new byte[]{0, (byte) 0xFF},
                List.of(new byte[]{1, 2}, new byte[]{3}));

        assertThat(request.baseState()).isEqualTo("AP8=");
        assertThat(request.updates()).containsExactly("AQI=", "Aw==");
    }

    @Test
    void shouldAllowMissingBaseStateButRejectEmptyYjsPayloads() {
        assertThat(YjsMergeRequest.from(null, List.of(new byte[]{1})).baseState()).isNull();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> YjsMergeRequest.from(new byte[0], List.of(new byte[]{1})));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> YjsMergeRequest.from(null, List.of(new byte[0])));
    }

    @Test
    void shouldDecodeMergedStateAndRejectMalformedResponse() {
        assertThat(new YjsMergeResponse("AP8=").decodeMergedState()).containsExactly(0, (byte) 0xFF);
        assertThatThrownBy(() -> new YjsMergeResponse("not-base64!").decodeMergedState())
                .isInstanceOf(YjsMergeException.class);
    }
}
