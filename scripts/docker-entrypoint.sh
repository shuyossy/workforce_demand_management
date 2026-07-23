#!/usr/bin/env bash
set -euo pipefail

# 必須の DB 接続情報（01-datasource-postgres.cli が ${env.*} で参照）を早期検証する。
# 未設定のまま起動すると WildFly 深部の expression 解決エラーになり原因が分かりにくいため、
# ここで明示的に失敗させる。
: "${DB_URL:?DB_URL is required (例: jdbc:postgresql://host.docker.internal:5432/rcb)}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

# APP_CONTEXT_ROOT は未指定なら既定 /rcb（jboss-web.xml の既定値と揃える）。
# 02-system-properties.cli が ${env.APP_CONTEXT_ROOT} を expression として
# standalone.xml に書き込むため、boot 時に必ず解決できるよう export しておく。
export APP_CONTEXT_ROOT="${APP_CONTEXT_ROOT:-/rcb}"

# WildFly 起動前に CLI を offline（embed-server）で standalone.xml へ適用する。
# 01〜04 は online（wildfly-maven-plugin 経由のローカル開発/E2E）と共用の冪等スクリプト
# のため内容は複製せず、embed-server セッションで包んで 1 回の embed で連続適用する。
# boot 前のコンテナには online 接続先が存在しないので、embed-server を介さない
# `jboss-cli.sh --file` 実行は全 CLI が失敗し datasource 未登録のまま起動してしまう。
{
  echo "embed-server --std-out=echo"
  cat /opt/jboss/cli/01-datasource-postgres.cli \
    /opt/jboss/cli/02-system-properties.cli \
    /opt/jboss/cli/03-logging.cli \
    /opt/jboss/cli/04-proxy-forwarding.cli
  echo "stop-embedded-server"
} >/tmp/docker-offline-config.cli
"$JBOSS_HOME"/bin/jboss-cli.sh --file=/tmp/docker-offline-config.cli

# 起動
exec "$JBOSS_HOME"/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0
