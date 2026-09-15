package com.jacolp.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2Rs256DeploymentConfigurationTest {

    @Test
    void applicationConfigurationUsesProfileProvidedPemLocations() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertThat(yaml).contains(
                "browser-login:",
                "csrf-enabled: false",
                "issuer: ${OAUTH2_RS256_ISSUER:core-node}",
                "audience: ${OAUTH2_RS256_AUDIENCE:core-node-api}",
                "private-key-location: ${jacolp.oauth2.rs256.private-key-location}",
                "public-key-location: ${jacolp.oauth2.rs256.public-key-location}");
    }

    @Test
    void dockerConfigurationMountsOnlyAnExternalPemDirectory() throws Exception {
        String dockerYaml = Files.readString(Path.of("..", "application-docker.yml"));
        String compose = Files.readString(Path.of("..", "docker-compose.yml"));
        String environmentTemplate = Files.readString(Path.of("..", ".env.example"));

        assertThat(dockerYaml).contains(
                "browser-login:",
                "csrf-enabled: false",
                "private-key-location: ${OAUTH2_RS256_PRIVATE_KEY_LOCATION:file:/run/secrets/oauth2-rs256/private.pem}",
                "public-key-location: ${OAUTH2_RS256_PUBLIC_KEY_LOCATION:file:/run/secrets/oauth2-rs256/public.pem}");
        assertThat(compose).contains(
                "OAUTH2_RS256_KEY_DIRECTORY",
                ":/run/secrets/oauth2-rs256:ro");
        assertThat(environmentTemplate).contains(
                "OAUTH2_RS256_KEY_DIRECTORY=/absolute/path/to/oauth2-rs256-keys")
                .doesNotContain("-----BEGIN PRIVATE KEY-----");
    }
}
