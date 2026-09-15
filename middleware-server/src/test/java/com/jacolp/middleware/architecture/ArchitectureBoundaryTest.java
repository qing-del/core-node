package com.jacolp.middleware.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureBoundaryTest {

    private static final Pattern PERSISTENCE_IMPORT = Pattern.compile(
            "^\\s*import\\s+com\\.jacolp\\.middleware\\.module\\.([a-z0-9-]+)\\.biz"
                    + "\\.infrastructure\\.persistence\\.(?:dataobject|mapper)\\..*;",
            Pattern.MULTILINE);
    private static final Pattern MAPPER_IMPORT = Pattern.compile(
            "^\\s*import\\s+.*\\.infrastructure\\.persistence\\.mapper\\..*;", Pattern.MULTILINE);
    private static final Pattern DOMAIN_FRAMEWORK_IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:org\\.springframework\\.web\\.|org\\.apache\\.ibatis\\.|org\\.mybatis\\.).*;",
            Pattern.MULTILINE);
    private static final Pattern RABBIT_TEMPLATE_IMPORT = Pattern.compile(
            "^\\s*import\\s+org\\.springframework\\.amqp\\.rabbit\\.core\\.RabbitTemplate;",
            Pattern.MULTILINE);

    @Test
    void bizPomsDoNotDependOnOtherBizModules() throws Exception {
        List<String> violations = new ArrayList<>();
        for (BizModule module : bizModules(repositoryRoot())) {
            for (String artifactId : dependencyArtifactIds(module.pom())) {
                if (artifactId.matches("middleware-module-.+-biz")) {
                    violations.add(module.pom() + " -> " + artifactId);
                }
            }
        }
        assertNoViolations("biz POM must not depend on another *-biz module", violations);
    }

    @Test
    void modulesDoNotImportOtherModulesPersistenceTypes() throws Exception {
        List<String> violations = new ArrayList<>();
        for (BizModule module : bizModules(repositoryRoot())) {
            for (Path source : moduleJavaFiles(module.root())) {
                Matcher matcher = PERSISTENCE_IMPORT.matcher(Files.readString(source));
                while (matcher.find()) {
                    String importedModule = matcher.group(1);
                    if (!module.name().equals(importedModule)) {
                        violations.add(source + " -> " + matcher.group().trim());
                    }
                }
            }
        }
        assertNoViolations("a module must not import another module's persistence mapper or data object", violations);
    }

    @Test
    void controllersDoNotImportMappers() throws Exception {
        List<String> violations = new ArrayList<>();
        for (BizModule module : bizModules(repositoryRoot())) {
            for (Path source : moduleJavaFiles(module.root())) {
                if (source.getFileName().toString().endsWith("Controller.java")) {
                    collectMatches(source, MAPPER_IMPORT, violations);
                }
            }
        }
        assertNoViolations("controllers must not import persistence mappers", violations);
    }

    @Test
    void domainCodeDoesNotDependOnWebOrMybatis() throws Exception {
        List<String> violations = new ArrayList<>();
        for (BizModule module : bizModules(repositoryRoot())) {
            for (Path source : moduleJavaFiles(module.root())) {
                if (hasPathSegment(source, "domain")) {
                    collectMatches(source, DOMAIN_FRAMEWORK_IMPORT, violations);
                }
            }
        }
        assertNoViolations("domain code must not import Spring MVC or MyBatis", violations);
    }

    @Test
    void serverMainContainsOnlyBootstrapAndConfiguration() throws Exception {
        Path serverMain = repositoryRoot().resolve("middleware-server/src/main/java");
        List<String> violations = new ArrayList<>();
        for (Path source : javaFiles(serverMain)) {
            String relative = serverMain.relativize(source).toString().replace('\\', '/');
            boolean allowed = relative.equals("com/jacolp/MiddlewareServerApplication.java")
                    || relative.startsWith("com/jacolp/config/");
            if (!allowed) {
                violations.add(source.toString());
            }
        }
        assertNoViolations("server main must contain only the bootstrap class and configuration", violations);
    }

    @Test
    void auditReviewServicesDoNotCallOtherModulesWriteApis() throws Exception {
        Path auditServices = repositoryRoot().resolve(
                "middleware-module-audit/middleware-module-audit-biz/src/main/java/"
                        + "com/jacolp/audit/application/service");
        Pattern forbiddenWriteApi = Pattern.compile(
                "^\\s*import\\s+com\\.jacolp\\.module\\.(?:note|media)\\.api\\."
                        + "(?:NoteAuditApplyApi|MediaAuditApplyApi|command\\.(?:ApplyNoteAuditCommand|"
                        + "ApplyTagAuditCommand|ApplyMediaAuditCommand)).*;",
                Pattern.MULTILINE);
        List<String> violations = new ArrayList<>();
        for (Path source : javaFiles(auditServices)) {
            if (source.getFileName().toString().equals("AuditReviewService.java")
                    || source.getFileName().toString().equals("ImageAuditReviewCompatibilityService.java")) {
                collectMatches(source, forbiddenWriteApi, violations);
            }
        }
        assertNoViolations("audit review services must publish events instead of calling source write APIs", violations);
    }

    @Test
    void auditModuleDoesNotDependOnOwnerModules() throws Exception {
        List<String> violations = new ArrayList<>();
        for (BizModule module : bizModules(repositoryRoot())) {
            List<String> dependencies = dependencyArtifactIds(module.pom());
            if (module.name().equals("audit")) {
                dependencies.stream()
                        .filter(id -> id.matches("middleware-module-(?:note|media|system)-api"))
                        .forEach(id -> violations.add(module.pom() + " -> " + id));
            }
            Pattern forbiddenImport = switch (module.name()) {
                case "audit" -> Pattern.compile("^\\s*import\\s+com\\.jacolp\\.module\\.(?:note|media|system)\\.api\\..*;",
                        Pattern.MULTILINE);
                default -> null;
            };
            if (forbiddenImport != null) {
                for (Path source : moduleJavaFiles(module.root())) {
                    collectMatches(source, forbiddenImport, violations);
                }
            }
        }
        assertNoViolations("audit module must not depend on owner modules", violations);
    }

    @Test
    void criticalWorkflowModulesPublishOnlyThroughOutboxInfrastructure() throws Exception {
        List<String> violations = new ArrayList<>();
        for (BizModule module : bizModules(repositoryRoot())) {
            if (List.of("audit", "note", "media", "system").contains(module.name())) {
                for (Path source : moduleJavaFiles(module.root())) {
                    collectMatches(source, RABBIT_TEMPLATE_IMPORT, violations);
                }
            }
        }
        assertNoViolations("critical workflows must persist Outbox events instead of using RabbitTemplate directly",
                violations);
    }

    @Test
    void externalSideEffectsStayBehindTheirConsumers() throws Exception {
        List<String> violations = new ArrayList<>();
        Path systemSources = repositoryRoot().resolve(
                "middleware-module-system/middleware-module-system-biz/src/main/java");
        for (Path source : javaFiles(systemSources)) {
            String text = Files.readString(source);
            if (text.contains("org.springframework.mail.javamail.JavaMailSender")
                    && !source.getFileName().toString().equals("SmtpEmailGateway.java")) {
                violations.add(source + " -> JavaMailSender");
            }
        }
        Path mediaSources = repositoryRoot().resolve(
                "middleware-module-media/middleware-module-media-biz/src/main/java");
        for (Path source : javaFiles(mediaSources)) {
            if (Files.readString(source).contains("ossOperator.delete(")
                    && !source.getFileName().toString().equals("MediaResourceDeleteEventHandler.java")) {
                violations.add(source + " -> ossOperator.delete");
            }
        }
        assertNoViolations("SMTP and OSS deletion must execute only in their dedicated consumers", violations);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("middleware-server"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root from " + Path.of("").toAbsolutePath());
    }

    private static List<BizModule> bizModules(Path root) throws IOException {
        List<BizModule> modules = new ArrayList<>();
        try (Stream<Path> moduleRoots = Files.list(root)) {
            for (Path moduleRoot : moduleRoots.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("middleware-module-"))
                    .toList()) {
                String name = moduleRoot.getFileName().toString().substring("middleware-module-".length());
                Path bizRoot = moduleRoot.resolve("middleware-module-" + name + "-biz");
                Path pom = bizRoot.resolve("pom.xml");
                if (Files.isRegularFile(pom)) {
                    modules.add(new BizModule(name, moduleRoot, pom));
                }
            }
        }
        return modules;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static List<Path> moduleJavaFiles(Path moduleRoot) throws IOException {
        try (Stream<Path> files = Files.walk(moduleRoot)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .toList();
        }
    }

    private static List<String> dependencyArtifactIds(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        Document document = builder.parse(pom.toFile());

        List<String> artifactIds = new ArrayList<>();
        for (int i = 0; i < document.getElementsByTagNameNS("*", "dependency").getLength(); i++) {
            Element dependency = (Element) document.getElementsByTagNameNS("*", "dependency").item(i);
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            if ("com.jacolp".equals(groupId) && artifactId != null) {
                artifactIds.add(artifactId);
            }
        }
        return artifactIds;
    }

    private static String childText(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(element.getLocalName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    private static void collectMatches(Path source, Pattern pattern, List<String> violations) throws IOException {
        Matcher matcher = pattern.matcher(Files.readString(source));
        while (matcher.find()) {
            violations.add(source + " -> " + matcher.group().trim());
        }
    }

    private static boolean hasPathSegment(Path path, String segment) {
        for (Path part : path) {
            if (segment.equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static void assertNoViolations(String rule, List<String> violations) {
        assertTrue(violations.isEmpty(), () -> rule + System.lineSeparator() + String.join(System.lineSeparator(), violations));
    }

    private record BizModule(String name, Path root, Path pom) {
    }
}
