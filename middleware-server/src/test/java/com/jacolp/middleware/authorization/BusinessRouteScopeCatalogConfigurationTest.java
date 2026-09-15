package com.jacolp.middleware.authorization;

import com.jacolp.config.BusinessRouteScopeCatalogConfiguration;
import com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationEntry;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRouteScopeCatalogConfigurationTest {

    private static final Set<String> EXCEPTIONS = Set.of(
            "POST /user/user/register", "POST /user/user/resend-activation", "GET /user/user/active/{token}",
            "POST /user/user/active-code");
    private static final List<String> CONTROLLERS = List.of(
            "com.jacolp.audio.controller.admin.AudioController",
            "com.jacolp.audio.controller.user.AudioController",
            "com.jacolp.audit.application.controller.admin.AuditController",
            "com.jacolp.audit.application.controller.admin.ImageAuditReviewCompatibilityController",
            "com.jacolp.media.controller.AdminImageController",
            "com.jacolp.media.controller.UserImageController",
            "com.jacolp.media.controller.UserImageAuditApplicationController",
            "com.jacolp.note.application.controller.admin.NoteController",
            "com.jacolp.note.application.controller.admin.NoteRelationController",
            "com.jacolp.note.application.controller.admin.TagController",
            "com.jacolp.note.application.controller.user.NoteController",
            "com.jacolp.note.application.controller.user.NoteRelationController",
            "com.jacolp.note.application.controller.user.PublicNoteController",
            "com.jacolp.note.application.controller.user.TagController",
            "com.jacolp.note.application.controller.user.UserNoteAuditApplicationController",
            "com.jacolp.note.interfaces.web.admin.TopicController",
            "com.jacolp.note.interfaces.web.user.TopicController",
            "com.jacolp.system.web.controller.admin.EmailController",
            "com.jacolp.system.web.controller.admin.UserController",
            "com.jacolp.system.web.controller.user.EmailController",
            "com.jacolp.system.web.controller.user.UserController");

    @Test
    void everySpringBusinessMappingHasExactlyOneCatalogueEntryOrOneExplicitException() throws Exception {
        Set<String> mappedRoutes = mappedBusinessRoutes();
        Set<String> protectedRoutes = new LinkedHashSet<>(mappedRoutes);
        protectedRoutes.removeAll(EXCEPTIONS);
        Map<String, Long> policyRoutes = BusinessRouteScopeCatalogConfiguration.entries().stream()
                .collect(Collectors.groupingBy(BusinessRouteScopeCatalogConfigurationTest::route, Collectors.counting()));

        assertThat(mappedRoutes).hasSize(120);
        assertThat(mappedRoutes).containsAll(EXCEPTIONS);
        assertThat(EXCEPTIONS).hasSize(4);
        assertThat(protectedRoutes).hasSize(116);
        assertThat(policyRoutes).hasSize(116);
        assertThat(policyRoutes.keySet()).containsExactlyInAnyOrderElementsOf(protectedRoutes);
        assertThat(policyRoutes.values()).allMatch(count -> count == 1L);
    }

    @Test
    void documentationMatchesTheExecutableCatalogueCountsAndCoreScopeRules() throws IOException {
        String document = Files.readString(repositoryRoot().resolve(
                "static/document/security/phase5-business-route-scope-catalog.md"));
        long documentedEntries = document.lines().filter(line -> line.matches("\\| \\d+ \\|.*")).count();

        assertThat(documentedEntries).isEqualTo(116);
        assertThat(document.lines().filter(line -> line.startsWith("## `/user/**`")).toList())
                .containsExactly("## `/user/**`：user client（71 bearer routes）");
        assertThat(document.lines().filter(line -> line.startsWith("## `/admin/**`")).toList())
                .containsExactly("## `/admin/**`：admin client（45 bearer routes）");
        assertThat(document).contains("120 个", "75 个 user", "45 个 admin", "116 个是 bearer", "4 个是下文明确排除");
        assertThat(document).contains("`GET /user/note/source/{id}`", "`audit:write`", "`audit:manage`",
                "`note:read` + `media:read`", "`note:write` + `media:read`");
    }

    private static Set<String> mappedBusinessRoutes() throws Exception {
        InspectingHandlerMapping mapping = new InspectingHandlerMapping();
        Set<String> routes = new LinkedHashSet<>();
        for (String controllerName : CONTROLLERS) {
            Class<?> controller = Class.forName(controllerName);
            for (Method method : controller.getDeclaredMethods()) {
                RequestMappingInfo info = mapping.mapping(method, controller);
                if (info == null) continue;
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                Set<RequestMethod> effectiveMethods = methods.isEmpty() ? Set.of(RequestMethod.GET) : methods;
                for (String path : info.getPatternValues()) {
                    if (!path.startsWith("/user/") && !path.startsWith("/admin/")) continue;
                    for (RequestMethod requestMethod : effectiveMethods) {
                        routes.add(requestMethod.name() + " " + path);
                    }
                }
            }
        }
        return routes;
    }

    private static String route(BusinessRouteAuthorizationEntry entry) {
        return entry.method().name() + " " + entry.pathPattern();
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("static"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    private static final class InspectingHandlerMapping extends RequestMappingHandlerMapping {
        private RequestMappingInfo mapping(Method method, Class<?> controller) {
            return getMappingForMethod(method, controller);
        }
    }
}
