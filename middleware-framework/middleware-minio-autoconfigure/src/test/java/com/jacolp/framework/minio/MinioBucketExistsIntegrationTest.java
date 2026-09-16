package com.jacolp.framework.minio;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.BucketExistsArgs;
import io.minio.ListBucketsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/** Uses the same MinIO Java SDK call as the application against an explicitly configured endpoint. */
@Tag("integration")
@EnabledIfSystemProperty(named = "minio.integration-test", matches = "true")
class MinioBucketExistsIntegrationTest {

    @Test
    void canCheckConfiguredDocumentBucket() throws Exception {
        Path configFile = configFile();
        StandardEnvironment environment = loadEnvironment(configFile);
        String endpoint = requiredProperty(environment, "jacolp.minio.endpoint", configFile);
        String accessKey = requiredProperty(environment, "jacolp.minio.access-key", configFile);
        String secretKey = requiredProperty(environment, "jacolp.minio.secret-key", configFile);
        String bucket = propertyOrDefault(environment, "jacolp.minio.bucket.document", "document");
        boolean useSystemProxy = environment.getProperty(
                "jacolp.minio.use-system-proxy", Boolean.class, false);
        Path traceFile = Path.of(
                System.getProperty("minio.trace.file", "/private/tmp/minio-java-http-trace.log"));
        Files.writeString(traceFile, "endpoint=" + endpoint + ", bucket=" + bucket + System.lineSeparator());
        Path proxyTraceFile = Path.of(System.getProperty(
                "minio.proxy.trace.file", "/private/tmp/minio-java-http-proxy-trace.log"));
        Files.writeString(proxyTraceFile, "endpoint=" + endpoint + ", bucket=" + bucket + System.lineSeparator());

        System.out.println("Java proxy selector for MinIO endpoint: "
                + ProxySelector.getDefault().select(URI.create(endpoint)));
        System.out.println("Java proxy selector implementation: "
                + ProxySelector.getDefault().getClass().getName());
        System.out.println("java.net.useSystemProxies: "
                + System.getProperty("java.net.useSystemProxies"));
        System.out.println("HTTP proxy system properties: http.proxyHost="
                + System.getProperty("http.proxyHost")
                + ", http.proxyPort=" + System.getProperty("http.proxyPort")
                + ", https.proxyHost=" + System.getProperty("https.proxyHost")
                + ", https.proxyPort=" + System.getProperty("https.proxyPort"));
        System.out.println("Configured MinIO system proxy: " + useSystemProxy);

        System.out.println("MinIO direct HTTP trace file: " + traceFile);
        System.out.println("MinIO system-proxy HTTP trace file: " + proxyTraceFile);

        MinioClient directClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient("direct", traceFile, true))
                .build();
        MinioClient applicationStyleClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient("system-proxy", proxyTraceFile, false))
                .build();
        MinioClient sdkDefaultClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        MinioProperties applicationProperties = new MinioProperties();
        applicationProperties.setEndpoint(endpoint);
        applicationProperties.setAccessKey(accessKey);
        applicationProperties.setSecretKey(secretKey);
        applicationProperties.setUseSystemProxy(useSystemProxy);
        MinioClient applicationClient = new MinioAutoConfiguration().minioClient(applicationProperties);

        boolean directExists = probe("direct/no-proxy", directClient, endpoint, bucket);
        probe("application-style/system-proxy", applicationStyleClient, endpoint, bucket);
        probe("sdk-default", sdkDefaultClient, endpoint, bucket);
        String applicationLabel = "application-configured/"
                + (useSystemProxy ? "system-proxy" : "no-proxy");
        boolean applicationExists = probe(applicationLabel, applicationClient, endpoint, bucket);

        assertThat(directExists)
                .as("MinIO bucket '%s' should exist at %s", bucket, endpoint)
                .isTrue();
        assertThat(applicationExists)
                .as("Application-configured MinIO client should follow jacolp.minio.use-system-proxy")
                .isTrue();
    }

    private static OkHttpClient httpClient(String label, Path traceFile, boolean noProxy) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (noProxy) {
            builder.proxy(Proxy.NO_PROXY);
        }
        return builder
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    String target = request.url().encodedPath()
                            + (request.url().encodedQuery() == null ? "" : "?" + request.url().encodedQuery());
                    try {
                        Response response = chain.proceed(request);
                        appendTrace(traceFile, label + " HTTP " + request.method() + " " + target
                                + " -> " + response.code()
                                + ", Server=" + response.header("Server")
                                + ", Content-Type=" + response.header("Content-Type")
                                + ", X-Amz-Request-Id=" + response.header("X-Amz-Request-Id")
                                + ", X-Minio-Error-Code=" + response.header("X-Minio-Error-Code")
                                + System.lineSeparator());
                        return response;
                    } catch (IOException exception) {
                        appendTrace(traceFile, label + " IO " + request.method() + " " + target
                                + " -> " + exception.getClass().getSimpleName() + ": " + exception.getMessage()
                                + System.lineSeparator());
                        throw exception;
                    }
                })
                .build();
    }

    private static boolean probe(String label, MinioClient minioClient, String endpoint, String bucket) {
        try {
            List<Bucket> buckets = minioClient.listBuckets(ListBucketsArgs.builder().build());
            System.out.println(label + " listBuckets OK: endpoint=" + endpoint + ", count=" + buckets.size());
        } catch (Exception exception) {
            System.out.println(label + " listBuckets FAILED: endpoint=" + endpoint
                    + ", cause=" + summarize(exception));
        }

        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).maxKeys(1).build());
            objects.iterator().hasNext();
            System.out.println(label + " listObjects OK: endpoint=" + endpoint + ", bucket=" + bucket);
        } catch (Exception exception) {
            System.out.println(label + " listObjects FAILED: endpoint=" + endpoint + ", bucket=" + bucket
                    + ", cause=" + summarize(exception));
        }

        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            System.out.println(label + " bucketExists=" + exists + ": endpoint=" + endpoint + ", bucket=" + bucket);
            return exists;
        } catch (Exception exception) {
            System.out.println(label + " bucketExists FAILED: endpoint=" + endpoint + ", bucket=" + bucket
                    + ", cause=" + summarize(exception));
            return false;
        }
    }

    private static StandardEnvironment loadEnvironment(Path configFile) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(configFile.getFileName().toString(), new FileSystemResource(configFile));
        for (PropertySource<?> source : sources) {
            propertySources.addFirst(source);
        }
        return environment;
    }

    private static Path configFile() {
        String configuredPath = System.getProperty("minio.test.config");
        Path path = configuredPath == null || configuredPath.isBlank()
                ? locateRepositoryRoot().resolve("middleware-server/src/main/resources/application-dev.yaml")
                : Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("MinIO test config file does not exist: " + path);
        }
        return path;
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve("middleware-server/src/main/resources/application-dev.yaml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root for MinIO test config");
    }

    private static String requiredProperty(StandardEnvironment environment, String key, Path configFile) {
        String value = environment.getProperty(key);
        return Objects.requireNonNull(value == null || value.isBlank() ? null : value,
                key + " must be configured in " + configFile);
    }

    private static String propertyOrDefault(StandardEnvironment environment, String key, String defaultValue) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String summarize(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private static void appendTrace(Path traceFile, String line) {
        try {
            Files.writeString(traceFile, line, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            System.out.println("Could not write MinIO HTTP trace: " + exception.getMessage());
        }
    }
}
