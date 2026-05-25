#!/usr/bin/env bash
set -euo pipefail

# E2E プロファイル用: WildFly に attach した JaCoCo agent (output=tcpserver) から
# jacococli の dump コマンドで通過行情報を取得する。
# WildFly 停止前に必ず実行することで、agent の append=false + tcpserver セッションの
# flush タイミングを確定させる（agent が突然 kill されると exec ファイルが 0 byte に
# なる事故を防ぐ）。
#
# 引数:
#   $1 jacococli.jar の絶対パス
#   $2 dump 出力先 destfile（target/jacoco-e2e.exec）
# 環境変数:
#   JACOCO_E2E_PORT   tcpserver ポート (既定 6300)
#   JACOCO_E2E_HOST   tcpserver ホスト (既定 localhost)
#   JACOCO_RESET      "true" なら dump 後にカウンタをリセット (既定 false)
#
# 失敗時の方針:
#   - tcpserver 接続失敗が 30 秒続いたら WARN を出して exit 0
#     （E2E カバレッジ未収集は merge 側で 0 byte ファイル除外として吸収）

JACOCO_CLI_JAR="${1:-}"
JACOCO_E2E_EXEC="${2:-}"
JACOCO_E2E_HOST="${JACOCO_E2E_HOST:-localhost}"
JACOCO_E2E_PORT="${JACOCO_E2E_PORT:-6300}"
JACOCO_RESET="${JACOCO_RESET:-false}"

if [ -z "$JACOCO_CLI_JAR" ] || [ ! -f "$JACOCO_CLI_JAR" ]; then
  echo "ERROR: jacococli.jar not found: ${JACOCO_CLI_JAR}" >&2
  exit 1
fi
if [ -z "$JACOCO_E2E_EXEC" ]; then
  echo "ERROR: dump destfile not specified" >&2
  exit 1
fi

mkdir -p "$(dirname "$JACOCO_E2E_EXEC")"

# JACOCO_RESET=true なら --reset を加える（pre-push 段 2 のような連続 E2E では false で OK）
RESET_ARG=()
if [ "$JACOCO_RESET" = "true" ]; then
  RESET_ARG+=(--reset)
fi

# TCP 接続を 30 秒 retry（WildFly の起動直後は agent ソケットが未 open のことがある）
for i in $(seq 1 30); do
  if (echo > "/dev/tcp/${JACOCO_E2E_HOST}/${JACOCO_E2E_PORT}") > /dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! java -jar "$JACOCO_CLI_JAR" dump \
    --address "$JACOCO_E2E_HOST" \
    --port "$JACOCO_E2E_PORT" \
    --destfile "$JACOCO_E2E_EXEC" \
    ${RESET_ARG[@]+"${RESET_ARG[@]}"}; then
  echo "WARN: jacococli dump failed; E2E coverage will be skipped from merge." >&2
  # 後段の merge が 0 byte ファイルを skip できるよう、サイズ 0 で touch する
  : > "$JACOCO_E2E_EXEC"
fi

echo "JaCoCo dump completed: ${JACOCO_E2E_EXEC} ($(wc -c < "$JACOCO_E2E_EXEC") bytes)"
