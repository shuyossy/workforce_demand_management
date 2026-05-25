#!/usr/bin/env bash
set -euo pipefail

# ローカル WildFly モード（WILDFLY_HOME 指定時）の冪等初期セットアップ。
# docker/dev/wildfly/Dockerfile と同等の処理を bash で行う：
#   1) 管理ユーザ焼き込み（add-user.sh）
#   2) PostgreSQL JDBC ドライバを WildFly モジュールとして配置
#   3) H2 JDBC ドライバを WildFly モジュールとして配置（E2E プロファイル用、独自 module 名）
# ドライババージョン定数 (PG_DRIVER_VERSION / H2_DRIVER_VERSION) は
# docker/dev/wildfly/Dockerfile と同期させること。

WILDFLY_HOME="${WILDFLY_HOME:?WILDFLY_HOME must be set for local setup}"
WILDFLY_USER="${WILDFLY_USER:-admin}"
WILDFLY_PASSWORD="${WILDFLY_PASSWORD:-admin}"
PG_DRIVER_VERSION="${PG_DRIVER_VERSION:-42.7.3}"
H2_DRIVER_VERSION="${H2_DRIVER_VERSION:-2.2.224}"

# 1) 管理ユーザ（冪等）
MGMT_USERS="$WILDFLY_HOME/standalone/configuration/mgmt-users.properties"
if grep -q "^${WILDFLY_USER}=" "$MGMT_USERS" 2> /dev/null; then
  echo "Management user '${WILDFLY_USER}' already registered. skip."
else
  echo "Registering management user '${WILDFLY_USER}'..."
  "$WILDFLY_HOME/bin/add-user.sh" "$WILDFLY_USER" "$WILDFLY_PASSWORD" --silent
fi

# 2) PostgreSQL JDBC モジュール（冪等）
MODULE_DIR="$WILDFLY_HOME/modules/system/layers/base/org/postgresql/main"
MODULE_JAR="$MODULE_DIR/postgresql.jar"
MODULE_XML="$MODULE_DIR/module.xml"

if [ -f "$MODULE_JAR" ] && [ -f "$MODULE_XML" ]; then
  echo "PostgreSQL JDBC module already installed (${MODULE_DIR}). skip."
else
  echo "Installing PostgreSQL JDBC driver ${PG_DRIVER_VERSION} as WildFly module..."
  mkdir -p "$MODULE_DIR"
  curl -fsSL \
    "https://repo1.maven.org/maven2/org/postgresql/postgresql/${PG_DRIVER_VERSION}/postgresql-${PG_DRIVER_VERSION}.jar" \
    -o "$MODULE_JAR"
  cat > "$MODULE_XML" << 'EOF'
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
fi

# 3) H2 JDBC モジュール（E2E プロファイル用、冪等）
# WildFly 同梱の古い H2 (com.h2database.h2:main) と衝突しないよう、独自 module 名
# (com.h2database.h2.e2e:main) で配置する。WildFly はモジュール名から
# `<base>/<name にドット→スラッシュ>/<slot>/` のパスでファイルを探すため、
# `com/h2database/h2/e2e/main/module.xml` の場所に置く必要がある。
H2_MODULE_DIR="$WILDFLY_HOME/modules/system/layers/base/com/h2database/h2/e2e/main"
H2_MODULE_JAR="$H2_MODULE_DIR/h2.jar"
H2_MODULE_XML="$H2_MODULE_DIR/module.xml"

if [ -f "$H2_MODULE_JAR" ] && [ -f "$H2_MODULE_XML" ]; then
  echo "H2 JDBC module (E2E) already installed (${H2_MODULE_DIR}). skip."
else
  echo "Installing H2 JDBC driver ${H2_DRIVER_VERSION} as WildFly module (E2E)..."
  mkdir -p "$H2_MODULE_DIR"
  curl -fsSL \
    "https://repo1.maven.org/maven2/com/h2database/h2/${H2_DRIVER_VERSION}/h2-${H2_DRIVER_VERSION}.jar" \
    -o "$H2_MODULE_JAR"
  cat > "$H2_MODULE_XML" << 'EOF'
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
fi
