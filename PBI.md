雛形

```
# ID:
- PBI名:
- ステータス: [to do/in progress/done]
- ユーザストーリー/背景
- 受け入れ基準
- 注意事項
- 指摘事項（in progressの場合のみ）
```

# ID: 1

- PBI名: windows対応
- ステータス: done
- 背景
  - 本ボイラープレートはこれまでmacで作成していたが、実際に導入する際はmacに限らず、windowsの場合もある
  - そこで開発環境をwindowsに移し、windowsでも動作するように見直すこととする
- 受け入れ基準
  - 本windows環境でビルド、テスト（verify※e2eプロファイル）、アプリ立ち上げ＆アクセスができることを確認すること
    - windows環境ではgit bashを利用する前提とすること
  - linux,mac,windows全てで動作するようにすること
- 注意事項
  - 現在claude codeを起動している環境はwindowsのgit bashである
  - 変更はできるだけ最小限とし、別環境へ影響を及ぼさないように注意する
- 対応内容（windows git bash で `-Pfast clean verify` / `-Pe2e verify` / アプリ起動・アクセスを実機確認済み。すべて mac/Linux に影響しない後方互換な修正）
  - `scripts/setup-lint-tools.mjs`: Node 20.12+/22 のセキュリティ修正で `mvnw.cmd`（バッチ）を `spawnSync` すると EINVAL になるため、Windows のみ `shell: true`＋実行ファイルの quote を付与
  - `scripts/wildfly-ensure-running.sh`:
    - `is_port_in_use` を lsof 無し環境（git bash）向けに bash `/dev/tcp` フォールバック対応（lsof→/dev/tcp→nc の順）
    - JaCoCo agent の Windows パス（バックスラッシュ）を standalone.sh の未クオート `$JAVA_OPTS` 展開でも壊れないようスラッシュへ正規化（`Error opening zip file` の解消）
  - `scripts/with-env.sh`: MSYS が `APP_CONTEXT_ROOT=/rcb` を Windows パス（`C:/Program Files/Git/rcb`）へ自動変換してコンテキストルートが壊れ 404 になるのを、`MSYS2_ENV_CONV_EXCL` で変換対象から除外して回避
  - `.gitattributes`: シェルスクリプト（`*.sh` / husky hook）を全プラットフォームで LF 強制（CRLF 混入による bash 起動失敗を防止）
