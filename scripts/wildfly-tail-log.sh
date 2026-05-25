#!/usr/bin/env bash
set -euo pipefail

# VSCode デバッグセッション中にアプリログを VSCode ターミナルへ流すための tail ラッパー。
# launch.json は "request": "attach" 型を採用しており、JDWP 仕様上 debuggee の stdout は
# Debug Console に流れない。CONSOLE handler は標準出力に出ているので、リダイレクト先
# (.dev-runtime/wildfly.log) を follow することで「F5 中に並走するログ表示」を実現する。
#
# wildfly-ensure-running.sh のモード分岐に合わせ、ローカル / Docker Compose を判定する。

DEV_RUNTIME_DIR=".dev-runtime"
COMPOSE_FILE="docker/dev/docker-compose.yml"

mkdir -p "$DEV_RUNTIME_DIR"

if [ -n "${WILDFLY_HOME:-}" ]; then
  # ── ローカル WildFly モード ──
  # 起動直後でファイル未生成のレースを避けるため touch しておく
  # (tail -F はファイル消失/再作成に追随するが、初回 ENOENT のメッセージを抑止)。
  touch "$DEV_RUNTIME_DIR/wildfly.log"
  exec tail -F -n 0 "$DEV_RUNTIME_DIR/wildfly.log"
fi

# ── Docker Compose モード ──
# docker compose の logs follow にフォールバック。サービス名は wildfly。
exec docker compose -f "$COMPOSE_FILE" logs -f --tail=0 wildfly
