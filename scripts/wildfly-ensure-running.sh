#!/usr/bin/env bash
set -euo pipefail

# WildFly 起動エントリポイント。WILDFLY_HOME の有無で起動モードを切替える：
#   - 未設定/空    : Docker Compose モード（docker/dev/docker-compose.yml）
#   - パス指定済み : ローカル WildFly モード（$WILDFLY_HOME/bin/standalone.sh）
# どちらのモードでも HTTP/Management/Debug を 0.0.0.0 にバインドし、社内ネットワーク内の
# 別マシンからも疎通可能にする。詳細は docs/docs/01-getting-started/env-setup.md 参照。

COMPOSE_FILE="docker/dev/docker-compose.yml"
DEV_RUNTIME_DIR=".dev-runtime"
WILDFLY_HOME="${WILDFLY_HOME:-}"
HOST="${WILDFLY_HOST:-localhost}"
MGMT="${WILDFLY_MGMT_PORT:-9990}"
DEBUG_PORT="${WILDFLY_DEBUG_PORT:-8787}"
# E2E プロファイル時 (-Pe2e) は JaCoCo agent を WildFly JVM に attach するため
# JAVA_OPTS を上書きする。CLI / Flyway / Playwright / dump の順序は pom.xml の e2e
# プロファイル側で execution としてバインドする。
E2E_MODE="${E2E_MODE:-0}"
JACOCO_AGENT_JAR="${JACOCO_AGENT_JAR:-}"
JACOCO_E2E_EXEC="${JACOCO_E2E_EXEC:-}"
JACOCO_E2E_PORT="${JACOCO_E2E_PORT:-6300}"
USER_NAME="${WILDFLY_USER:-admin}"
PASS="${WILDFLY_PASSWORD:-admin}"

# server-state=running まで待機する。
# /management の TCP 接続成功 (401 含む) だけだと WildFly が boot 途中の状態でも
# 200 を返してしまい、直後の :execute-commands 等が「WFLYCTL0379: System boot is
# in process」で弾かれる。digest auth 付きで :read-attribute(name=server-state)
# を投げ、結果 JSON に "running" を含むまで待つことで boot 完了を保証する。
wait_for_mgmt() {
  local label="$1"
  local body='{"operation":"read-attribute","name":"server-state"}'
  for i in $(seq 1 90); do
    response="$(
      curl -s --max-time 2 \
        --digest -u "${USER_NAME}:${PASS}" \
        -H "Content-Type: application/json" \
        -X POST "http://${HOST}:${MGMT}/management" \
        --data "$body" 2> /dev/null || true
    )"
    if echo "$response" | grep -q '"running"'; then
      echo "WildFly (${label}) is ready (server-state=running) after ${i}s"
      return 0
    fi
    sleep 1
  done
  return 1
}

is_port_in_use() {
  # Mac/Linux 共通で動く lsof ベース。lsof が無ければ nc にフォールバック。
  if command -v lsof > /dev/null 2>&1; then
    lsof -nP -iTCP:"$1" -sTCP:LISTEN > /dev/null 2>&1
  else
    nc -z localhost "$1" > /dev/null 2>&1
  fi
}

# 既に management ポートが開いているときの分岐：
# - 通常モード (F5 再 attach など)：server-state=running まで待って再利用 (no-op exit 0)。
#   boot 中に :execute-commands を投げると WFLYCTL0379 で弾かれるため、TCP 接続だけでは
#   なく server-state を確認する 2 段構成にしている。
# - E2E モード：F5 デバッグ起動の WildFly は JaCoCo agent 非アタッチ・PostgresDS 構成で
#   E2E 要件を満たさない（agent は JVM 起動時にしか attach できないため流用不可）。
#   ただし wildfly-maven-plugin:deploy は pre-integration-test で package フェーズを
#   fork するため、同一 Maven 起動内で e2e-wildfly-start が複数回呼ばれる。既に E2E 用
#   WildFly を自分で起動している (= JaCoCo tcpserver ポートが listen 中) ケースは
#   そのまま再利用する。listen していなければ debug 用 WildFly と判定し、auto-stop →
#   fresh start で乗せ替える。
if curl -s --max-time 2 -o /dev/null "http://${HOST}:${MGMT}/management" 2> /dev/null; then
  if [ "$E2E_MODE" = "1" ]; then
    if is_port_in_use "$JACOCO_E2E_PORT"; then
      if wait_for_mgmt "pre-existing-e2e"; then
        echo "WildFly (E2E session) already running at ${HOST}:${MGMT}"
        exit 0
      fi
      echo "ERROR: management port is open but WildFly did not reach 'running' state." >&2
      exit 1
    fi
    echo "E2E mode: stopping pre-existing WildFly at ${HOST}:${MGMT} (no JaCoCo agent attached) for clean restart..."
    bash scripts/wildfly-stop.sh || true
    # 8080 / management / debug の全ポートが解放されるまで最大 30 秒待つ。
    # wildfly-stop.sh は graceful shutdown を要求するだけで完全停止を待たないため、
    # 後段の port guard との競合を避けるべくここでまとめて待機する。
    STOP_WAIT_PORTS=(8080 "$MGMT" "$DEBUG_PORT")
    for _ in $(seq 1 30); do
      all_free=true
      for port in "${STOP_WAIT_PORTS[@]}"; do
        if is_port_in_use "$port"; then
          all_free=false
          break
        fi
      done
      if $all_free; then
        break
      fi
      sleep 1
    done
    for port in "${STOP_WAIT_PORTS[@]}"; do
      if is_port_in_use "$port"; then
        echo "ERROR: failed to free port ${port} within 30s after stop." >&2
        exit 1
      fi
    done
    # 以降は通常のローカル起動フローへ合流（offline cleanup → fresh start）。
  else
    if wait_for_mgmt "pre-existing"; then
      echo "WildFly already running at ${HOST}:${MGMT}"
      exit 0
    fi
    echo "ERROR: management port is open but WildFly did not reach 'running' state." >&2
    exit 1
  fi
fi

if [ -z "$WILDFLY_HOME" ]; then
  # ── Docker Compose モード ──
  echo "Starting dev stack (postgres + wildfly) via docker compose..."
  docker compose -f "$COMPOSE_FILE" up -d --build
  if wait_for_mgmt "docker-compose"; then
    exit 0
  fi
  echo "ERROR: WildFly failed to start in 90s." >&2
  echo "Run: docker compose -f $COMPOSE_FILE logs wildfly" >&2
  exit 1
fi

# ── ローカル WildFly モード ──
if [ ! -x "$WILDFLY_HOME/bin/standalone.sh" ]; then
  echo "ERROR: WILDFLY_HOME=$WILDFLY_HOME does not point to a valid WildFly install" >&2
  echo "       (expected: \$WILDFLY_HOME/bin/standalone.sh)" >&2
  exit 1
fi

# ポート衝突ガード（Docker Compose 残骸対策）。E2E モードでは JaCoCo agent ポートも確認する。
GUARD_PORTS=(8080 "$MGMT" "$DEBUG_PORT")
if [ "$E2E_MODE" = "1" ]; then
  GUARD_PORTS+=("$JACOCO_E2E_PORT")
fi
for port in "${GUARD_PORTS[@]}"; do
  if is_port_in_use "$port"; then
    echo "ERROR: port ${port} is already in use." >&2
    echo "       Docker Compose の WildFly が残っていないか確認し、必要なら 'wildfly:stop' を実行してください。" >&2
    exit 1
  fi
done

mkdir -p "$DEV_RUNTIME_DIR"

# 冪等な初期セットアップ（管理ユーザ焼き込み + PostgreSQL JDBC モジュール配置）
bash scripts/wildfly-local-setup.sh

# 起動前 offline クリーンアップ。前回セッション（特に -Pe2e）が standalone.xml に
# 永続化した datasource / jdbc-driver 定義を embed-server CLI で消す。online CLI では
# reload-required の制約で同 JNDI 切替や driver remove ができないため、起動前に
# クリーンな状態へ戻して 01-datasource-*.cli の冪等再登録に備える。
echo "Cleaning up datasource residue from previous session via embed-server CLI..."
"$WILDFLY_HOME/bin/jboss-cli.sh" --file=wildfly/cli/00-offline-cleanup.cli \
  > "$DEV_RUNTIME_DIR/offline-cleanup.log" 2>&1 || {
  echo "ERROR: offline cleanup failed. See $DEV_RUNTIME_DIR/offline-cleanup.log" >&2
  cat "$DEV_RUNTIME_DIR/offline-cleanup.log" >&2
  exit 1
}

# 既起動 PID が生きていれば（curl チェックは上で済）何もしない
if [ -f "$DEV_RUNTIME_DIR/wildfly.pid" ]; then
  EXISTING_PID="$(cat "$DEV_RUNTIME_DIR/wildfly.pid")"
  if kill -0 "$EXISTING_PID" 2> /dev/null; then
    echo "WildFly PID ${EXISTING_PID} is alive but mgmt port not responding; waiting..."
    if wait_for_mgmt "local-existing"; then
      exit 0
    fi
    echo "ERROR: existing WildFly process not responding. Run 'wildfly:stop' first." >&2
    exit 1
  fi
  rm -f "$DEV_RUNTIME_DIR/wildfly.pid"
fi

if [ "$E2E_MODE" = "1" ]; then
  if [ -z "$JACOCO_AGENT_JAR" ] || [ ! -f "$JACOCO_AGENT_JAR" ]; then
    echo "ERROR: E2E_MODE=1 but JACOCO_AGENT_JAR not found: ${JACOCO_AGENT_JAR}" >&2
    exit 1
  fi
  if [ -z "$JACOCO_E2E_EXEC" ]; then
    echo "ERROR: E2E_MODE=1 but JACOCO_E2E_EXEC is empty" >&2
    exit 1
  fi
  # destfile の親ディレクトリを必ず用意（WildFly 側で書き出し失敗を防ぐ）。
  mkdir -p "$(dirname "$JACOCO_E2E_EXEC")"
  # 集計時の絞り込みは pom.xml jacoco-maven-plugin の <excludes> に一元化されているため、
  # agent 側では includes を指定せず全クラスを計装対象にする（パッケージ構造変更時の二重更新を回避）。
  JACOCO_AGENT_OPT="-javaagent:${JACOCO_AGENT_JAR}=destfile=${JACOCO_E2E_EXEC},output=tcpserver,address=*,port=${JACOCO_E2E_PORT},append=false"
  export JAVA_OPTS="${JAVA_OPTS:-} ${JACOCO_AGENT_OPT}"
  echo "E2E mode enabled: attaching JaCoCo agent (tcpserver port=${JACOCO_E2E_PORT}, destfile=${JACOCO_E2E_EXEC})"
fi

echo "Starting local WildFly at $WILDFLY_HOME (bind 0.0.0.0, debug *:${DEBUG_PORT})..."
nohup "$WILDFLY_HOME/bin/standalone.sh" \
  -b 0.0.0.0 \
  -bmanagement 0.0.0.0 \
  --debug "*:${DEBUG_PORT}" \
  > "$DEV_RUNTIME_DIR/wildfly.log" 2>&1 &
echo $! > "$DEV_RUNTIME_DIR/wildfly.pid"

if wait_for_mgmt "local"; then
  exit 0
fi

echo "ERROR: local WildFly failed to start in 90s." >&2
echo "Log: $DEV_RUNTIME_DIR/wildfly.log" >&2
PID="$(cat "$DEV_RUNTIME_DIR/wildfly.pid" 2> /dev/null || true)"
if [ -n "$PID" ]; then
  kill "$PID" 2> /dev/null || true
fi
rm -f "$DEV_RUNTIME_DIR/wildfly.pid"
exit 1
