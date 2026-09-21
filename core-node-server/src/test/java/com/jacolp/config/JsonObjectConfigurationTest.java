package com.jacolp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JsonObjectConfigurationTest {

    private final ObjectMapper objectMapper = new JsonObjectConfiguration().objectMapper();

    @Test
    void serializesAndDeserializesOutboxInstants() throws Exception {
        Instant instant = Instant.parse("2026-08-15T00:00:00Z");

        String json = objectMapper.writeValueAsString(new InstantValue(instant));

        assertThat(json).isEqualTo("{\"occurredAt\":\"2026-08-15T00:00:00Z\"}");
        assertThat(objectMapper.readValue(json, InstantValue.class).occurredAt()).isEqualTo(instant);
    }

    @Test
    void retainsEstablishedLocalDateTimeFormat() throws Exception {
        LocalDateTime value = LocalDateTime.of(2026, 8, 15, 1, 2, 3);

        String json = objectMapper.writeValueAsString(new LocalDateTimeValue(value));

        assertThat(json).isEqualTo("{\"value\":\"2026-08-15 01:02:03\"}");
        assertThat(objectMapper.readValue(json, LocalDateTimeValue.class).value()).isEqualTo(value);
    }

    private record InstantValue(Instant occurredAt) {}

    private record LocalDateTimeValue(LocalDateTime value) {}
}
