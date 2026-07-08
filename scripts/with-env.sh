#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env}"
if [ -f "$ENV_FILE" ]; then
  set -a
  . "$ENV_FILE"
  set +a
fi

# Windows(git bash / MSYS)対策:
# MSYS ランタイムはネイティブ Windows プロセス（java.exe など）を起動する際、
# POSIX 絶対パス風の環境変数値を Windows パスへ自動変換する。
# 例: APP_CONTEXT_ROOT=/rcb → C:/Program Files/Git/rcb
# これによりコンテキストルートが壊れ WildFly 上のアプリが 404 になるため、
# 該当変数を変換対象から除外する。mac/Linux では未使用の環境変数となり無害。
export MSYS2_ENV_CONV_EXCL="${MSYS2_ENV_CONV_EXCL:+${MSYS2_ENV_CONV_EXCL};}APP_CONTEXT_ROOT"

exec "$@"
