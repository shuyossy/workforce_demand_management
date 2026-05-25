# ========== Stage 1: Build ==========
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# 依存解決を先に行うため pom.xml / mvnw を先にコピー
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:resolve

# ソースコード
COPY src ./src

# パッケージング（テストは CI で実施済み前提）
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

# ========== Stage 2: Runtime ==========
FROM quay.io/wildfly/wildfly:32.0.1.Final-jdk17 AS runtime

USER root

# CLI スクリプト適用（offline モード、起動前）
COPY wildfly/cli/ /opt/jboss/cli/
COPY scripts/docker-entrypoint.sh /opt/jboss/docker-entrypoint.sh
RUN chmod +x /opt/jboss/docker-entrypoint.sh

# WAR を deployments に配置
COPY --from=build /app/target/rcb-*.war /opt/jboss/wildfly/standalone/deployments/rcb.war

USER jboss

EXPOSE 8080 9990

ENTRYPOINT ["/opt/jboss/docker-entrypoint.sh"]
