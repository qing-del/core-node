package com.jacolp.document.controller;

import com.jacolp.document.application.share.DocumentShareLinkRedemptionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocumentShareLinkRedemptionControllerTest {

    @Test
    void redirectsWithoutExposingTheDocumentOrCachingTheCode() {
        DocumentShareLinkRedemptionController controller = new DocumentShareLinkRedemptionController(
                mock(DocumentShareLinkRedemptionService.class), "https://app.example.test/");

        var response = controller.redirect("abc");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).hasToString("https://app.example.test/share/documents/abc");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }
}
