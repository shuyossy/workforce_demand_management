#!/usr/bin/env bash
set -euo pipefail

# WildFly 停止エントリポイント。wildfly-ensure-running.sh と同様に WILDFLY_HOME で分岐：
#   - 未設定/空    : docker compose down
#   - パス指定済み : jboss-cli :shutdown → 残 PID kill → PID ファイル削除

COMPOSE_FILE="docker/dev/docker-compose.yml"
DEV_RUNTIME_DIR=".dev-runtime"
WILDFLY_HOME="${WILDFLY_HOME:-}"
HOST="${WILDFLY_HOST:-localhost}"
MGMT="${WILDFLY_MGMT_PORT:-9990}"
USER_NAME="${WILDFLY_USER:-admin}"
PASS="${WILDFLY_PASSWORD:-admin}"

if [ -z "$WILDFLY_HOME" ]; then
  echo "Stopping dev stack via docker compose..."
  docker compose -f "$COMPOSE_FILE" down
  exit 0
fi

# ── ローカル WildFly モード ──
if [ -x "$WILDFLY_HOME/bin/jboss-cli.sh" ]; then
  echo "Requesting graceful shutdown via jboss-cli..."
  "$WILDFLY_HOME/bin/jboss-cli.sh" \
    --connect "controller=${HOST}:${MGMT}" \
    --user="$USER_NAME" --password="$PASS" \
    --command=:shutdown || true
fi

if [ -f "$DEV_RUNTIME_DIR/wildfly.pid" ]; then
  PID="$(cat "$DEV_RUNTIME_DIR/wildfly.pid")"
  if kill -0 "$PID" 2> /dev/null; then
    echo "PID ${PID} still alive; sending SIGTERM..."
    kill "$PID" 2> /dev/null || true
  fi
  rm -f "$DEV_RUNTIME_DIR/wildfly.pid"
fi

echo "Local WildFly stopped."
