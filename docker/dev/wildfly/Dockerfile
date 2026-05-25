FROM quay.io/wildfly/wildfly:32.0.1.Final-jdk17

# 開発用 mgmt 管理者を焼き込む（dev 専用、本番イメージ Dockerfile（リポジトリ直下）とは別物）
USER root
RUN /opt/jboss/wildfly/bin/add-user.sh admin admin --silent

# PostgreSQL JDBC ドライバを WildFly モジュールとして配置
# （CLI スクリプトで使うため、起動時にモジュールが解決可能な状態にする）
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

# H2 JDBC ドライバを WildFly モジュールとして配置（E2E プロファイル用、独自 module 名）
# WildFly 同梱の古い H2 (com.h2database.h2) と衝突しないよう、独自 module 名で配置する。
ARG H2_DRIVER_VERSION=2.2.224
RUN mkdir -p /opt/jboss/wildfly/modules/system/layers/base/com/h2database/h2/e2e \
 && curl -fsSL "https://repo1.maven.org/maven2/com/h2database/h2/${H2_DRIVER_VERSION}/h2-${H2_DRIVER_VERSION}.jar" \
        -o /opt/jboss/wildfly/modules/system/layers/base/com/h2database/h2/e2e/h2.jar \
 && cat > /opt/jboss/wildfly/modules/system/layers/base/com/h2database/h2/e2e/module.xml <<'EOF'
<?xml version="1.0" ?>
<module xmlns="urn:jboss:module:1.5" name="com.h2database.h2.e2e">
  <resources>
    <resource-root path="h2.jar"/>
  </resources>
  <dependencies>
    <module name="javax.api"/>
    <module name="javax.transaction.api"/>
  </dependencies>
</module>
EOF

USER jboss

EXPOSE 8080 9990 8787
