# ============================================================
# Middleware Spring Boot backend image
# ============================================================

# ---------- Stage 1: Maven build ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy every current Maven descriptor first so dependency resolution is cached.
COPY pom.xml .
COPY middleware-dependencies/pom.xml middleware-dependencies/
COPY middleware-common/pom.xml middleware-common/
COPY middleware-common/middleware-common-core/pom.xml middleware-common/middleware-common-core/
COPY middleware-common/middleware-common-security/pom.xml middleware-common/middleware-common-security/
COPY middleware-common/middleware-common-web/pom.xml middleware-common/middleware-common-web/
COPY middleware-framework/pom.xml middleware-framework/
COPY middleware-framework/middleware-markdown-autoconfigure/pom.xml middleware-framework/middleware-markdown-autoconfigure/
COPY middleware-framework/middleware-markdown-starter/pom.xml middleware-framework/middleware-markdown-starter/
COPY middleware-framework/middleware-oss-autoconfigure/pom.xml middleware-framework/middleware-oss-autoconfigure/
COPY middleware-framework/middleware-oss-starter/pom.xml middleware-framework/middleware-oss-starter/
COPY middleware-framework/middleware-minio-autoconfigure/pom.xml middleware-framework/middleware-minio-autoconfigure/
COPY middleware-framework/middleware-minio-starter/pom.xml middleware-framework/middleware-minio-starter/
COPY middleware-framework/middleware-elasticsearch-autoconfigure/pom.xml middleware-framework/middleware-elasticsearch-autoconfigure/
COPY middleware-framework/middleware-elasticsearch-starter/pom.xml middleware-framework/middleware-elasticsearch-starter/
COPY middleware-module-audio/pom.xml middleware-module-audio/
COPY middleware-module-audio/middleware-module-audio-biz/pom.xml middleware-module-audio/middleware-module-audio-biz/
COPY middleware-module-audit/pom.xml middleware-module-audit/
COPY middleware-module-audit/middleware-module-audit-api/pom.xml middleware-module-audit/middleware-module-audit-api/
COPY middleware-module-audit/middleware-module-audit-biz/pom.xml middleware-module-audit/middleware-module-audit-biz/
COPY middleware-module-media/pom.xml middleware-module-media/
COPY middleware-module-media/middleware-module-media-api/pom.xml middleware-module-media/middleware-module-media-api/
COPY middleware-module-media/middleware-module-media-biz/pom.xml middleware-module-media/middleware-module-media-biz/
COPY middleware-module-note/pom.xml middleware-module-note/
COPY middleware-module-note/middleware-module-note-api/pom.xml middleware-module-note/middleware-module-note-api/
COPY middleware-module-note/middleware-module-note-biz/pom.xml middleware-module-note/middleware-module-note-biz/
COPY middleware-module-system/pom.xml middleware-module-system/
COPY middleware-module-system/middleware-module-system-api/pom.xml middleware-module-system/middleware-module-system-api/
COPY middleware-module-system/middleware-module-system-biz/pom.xml middleware-module-system/middleware-module-system-biz/
COPY middleware-module-document/pom.xml middleware-module-document/
COPY middleware-module-document/middleware-module-document-api/pom.xml middleware-module-document/middleware-module-document-api/
COPY middleware-module-document/middleware-module-document-biz/pom.xml middleware-module-document/middleware-module-document-biz/
COPY middleware-server/pom.xml middleware-server/

RUN mvn dependency:go-offline -B

# Copy the current project sources after the dependency cache layer.
COPY . .

RUN mvn clean package -pl middleware-server -am -DskipTests -B -Dmaven.test.skip=true

# ---------- Stage 2: runtime image ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN mkdir -p /app/data/markdown/input /app/data/markdown/output

COPY --from=builder /build/middleware-server/target/*.jar app.jar
COPY application-docker.yml /app/config/application.yml

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} \
  -Dspring.config.additional-location=/app/config/ \
  -jar app.jar"]
