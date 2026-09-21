# ============================================================
# CoreNode Spring Boot backend image
# ============================================================

# ---------- Stage 1: Maven build ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy every current Maven descriptor first so dependency resolution is cached.
COPY pom.xml .
COPY core-node-dependencies/pom.xml core-node-dependencies/
COPY core-node-common/pom.xml core-node-common/
COPY core-node-common/core-node-common-core/pom.xml core-node-common/core-node-common-core/
COPY core-node-common/core-node-common-security/pom.xml core-node-common/core-node-common-security/
COPY core-node-common/core-node-common-web/pom.xml core-node-common/core-node-common-web/
COPY core-node-framework/pom.xml core-node-framework/
COPY core-node-framework/core-node-markdown-autoconfigure/pom.xml core-node-framework/core-node-markdown-autoconfigure/
COPY core-node-framework/core-node-markdown-starter/pom.xml core-node-framework/core-node-markdown-starter/
COPY core-node-framework/core-node-oss-autoconfigure/pom.xml core-node-framework/core-node-oss-autoconfigure/
COPY core-node-framework/core-node-oss-starter/pom.xml core-node-framework/core-node-oss-starter/
COPY core-node-framework/core-node-minio-autoconfigure/pom.xml core-node-framework/core-node-minio-autoconfigure/
COPY core-node-framework/core-node-minio-starter/pom.xml core-node-framework/core-node-minio-starter/
COPY core-node-framework/core-node-elasticsearch-autoconfigure/pom.xml core-node-framework/core-node-elasticsearch-autoconfigure/
COPY core-node-framework/core-node-elasticsearch-starter/pom.xml core-node-framework/core-node-elasticsearch-starter/
COPY core-node-module-audio/pom.xml core-node-module-audio/
COPY core-node-module-audio/core-node-module-audio-biz/pom.xml core-node-module-audio/core-node-module-audio-biz/
COPY core-node-module-audit/pom.xml core-node-module-audit/
COPY core-node-module-audit/core-node-module-audit-api/pom.xml core-node-module-audit/core-node-module-audit-api/
COPY core-node-module-audit/core-node-module-audit-biz/pom.xml core-node-module-audit/core-node-module-audit-biz/
COPY core-node-module-media/pom.xml core-node-module-media/
COPY core-node-module-media/core-node-module-media-api/pom.xml core-node-module-media/core-node-module-media-api/
COPY core-node-module-media/core-node-module-media-biz/pom.xml core-node-module-media/core-node-module-media-biz/
COPY core-node-module-note/pom.xml core-node-module-note/
COPY core-node-module-note/core-node-module-note-api/pom.xml core-node-module-note/core-node-module-note-api/
COPY core-node-module-note/core-node-module-note-biz/pom.xml core-node-module-note/core-node-module-note-biz/
COPY core-node-module-system/pom.xml core-node-module-system/
COPY core-node-module-system/core-node-module-system-api/pom.xml core-node-module-system/core-node-module-system-api/
COPY core-node-module-system/core-node-module-system-biz/pom.xml core-node-module-system/core-node-module-system-biz/
COPY core-node-module-document/pom.xml core-node-module-document/
COPY core-node-module-document/core-node-module-document-api/pom.xml core-node-module-document/core-node-module-document-api/
COPY core-node-module-document/core-node-module-document-biz/pom.xml core-node-module-document/core-node-module-document-biz/
COPY core-node-open-api/pom.xml core-node-open-api/
COPY core-node-agent/pom.xml core-node-agent/
COPY core-node-server/pom.xml core-node-server/

RUN mvn dependency:go-offline -B

# Copy the current project sources after the dependency cache layer.
COPY . .

RUN mvn clean package -pl core-node-server -am -DskipTests -B -Dmaven.test.skip=true

# ---------- Stage 2: runtime image ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN mkdir -p /app/data/markdown/input /app/data/markdown/output

COPY --from=builder /build/core-node-server/target/*.jar app.jar
COPY application-docker.yml /app/config/application.yml

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} \
  -Dspring.config.additional-location=/app/config/ \
  -jar app.jar"]
