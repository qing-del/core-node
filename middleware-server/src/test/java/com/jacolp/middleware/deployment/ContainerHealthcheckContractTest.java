package com.jacolp.middleware.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContainerHealthcheckContractTest {

    @Test
    void backendImageHealthcheckUsesExposedActuatorLivenessEndpoint()
            throws Exception {
        Path root = locateRepositoryRoot();
        String serverPom = Files.readString(root.resolve("middleware-server/pom.xml"));
        String application = Files.readString(root.resolve(
                "middleware-server/src/main/resources/application.yaml"));
        String compose = Files.readString(root.resolve("docker-compose.yml"));
        String dockerfile = Files.readString(root.resolve("Dockerfile"));

        assertThat(serverPom).contains("<artifactId>spring-boot-starter-actuator</artifactId>");
        assertThat(application)
                .contains("management:")
                .contains("include: health")
                .contains("probes:")
                .contains("enabled: true")
                .contains("show-details: never");
        assertThat(compose)
                .contains("wget", "--spider")
                .contains("http://localhost:8080/actuator/health/liveness")
                .doesNotContain("curl\", \"-f\", \"http://localhost:8080/actuator/health");
        assertThat(dockerfile).contains("FROM eclipse-temurin:21-jre-alpine");
    }

    private static Path locateRepositoryRoot() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("docker-compose.yml"))
                    && Files.isRegularFile(directory.resolve("middleware-server/pom.xml"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
