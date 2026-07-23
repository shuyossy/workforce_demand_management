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

# PostgreSQL JDBC ドライバを WildFly モジュールとして配置
# （docker-entrypoint.sh が offline 適用する 01-datasource-postgres.cli の
#   driver-module-name=org.postgresql を boot 時に解決可能にする。
#   バージョンは pom.xml の <postgresql.version> と揃えること）
ARG PG_DRIVER_VERSION=42.7.3
RUN mkdir -p /opt/jboss/wildfly/modules/system/layers/base/org/postgresql/main \
 && curl -fsSL "https://repo1.maven.org/maven2/org/postgresql/postgresql/${PG_DRIVER_VERSION}/postgresql-${PG_DRIVER_VERSION}.jar" \
        -o /opt/jboss/wildfly/modules/system/layers/base/org/postgresql/main/postgresql.jar \
 && cat > /opt/jboss/wildfly/modules/system/layers/base/org/postgresql/main/module.xml <<'EOF'
<?xml version="1.0" ?>
<module xmlns="urn:jboss:module:1.5" name="org.postgresql">
  <resources>
    <resource-root path="postgresql.jar"/>
  </resources>
  <dependencies>
    <module name="javax.api"/>
    <module name="javax.transaction.api"/>
  </dependencies>
</module>
EOF

# CLI スクリプト配置（datasource 等は docker-entrypoint.sh が起動前に offline 適用）
COPY wildfly/cli/ /opt/jboss/cli/
COPY scripts/docker-entrypoint.sh /opt/jboss/docker-entrypoint.sh
RUN chmod +x /opt/jboss/docker-entrypoint.sh

# 標準記述子（web.xml）の ${env.*:default} 置換を build 時に standalone.xml へ焼き込む。
# boot 時 auto-deploy される rcb.war の web.xml で APP_JSF_PROJECT_STAGE / APP_SESSION_COOKIE_SECURE /
# APP_SESSION_TIMEOUT_MINUTES を解決させるため（online CLI 経路が無い本番向け、embed-server で永続化）。
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/opt/jboss/cli/05-ee-descriptor-replacement.cli

# WAR を deployments に配置
COPY --from=build /app/target/rcb-*.war /opt/jboss/wildfly/standalone/deployments/rcb.war

# root で実行した embed-server の残骸を掃除し、所有権を実行ユーザ jboss へ戻す。
# - standalone_xml_history / data / log / tmp はイメージレイヤに残すと、boot 時の履歴
#   ローテーション（ディレクトリ rename）が overlayfs のレイヤ跨ぎ制約で
#   WFLYCTL0056 (DirectoryNotEmptyException) になるため削除する（boot 時に再生成される）。
# - chown を怠ると boot 時に server.log へ書き込めず Permission denied で起動不能になる。
RUN rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history \
      /opt/jboss/wildfly/standalone/data \
      /opt/jboss/wildfly/standalone/log \
      /opt/jboss/wildfly/standalone/tmp \
 && chown -R jboss:root /opt/jboss/wildfly/standalone

USER jboss

EXPOSE 8080 9990

ENTRYPOINT ["/opt/jboss/docker-entrypoint.sh"]
