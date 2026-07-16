# Flyway

PostgreSQL のスキーマ管理は Flyway で行う。バージョン migration（本番・開発共通）と開発用シード（reset 時のみ）を分離する。

## ディレクトリ構成

| 種別                 | パス                                                  | 用途                                                                                                                                                                                                 |
| -------------------- | ----------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| バージョン migration | `src/main/resources/db/migration/V<n>__*.sql`         | 本番・開発共通                                                                                                                                                                                       |
| 開発/E2E シード      | `src/main/resources/db/dev-bootstrap/R__dev_seed.sql` | 本番共通の `db/migration` から分離。`db/dev-bootstrap` を `locations` に追加する `-Pe2e`（や開発用 flyway 実行）でのみ流れる。env 駆動デフォルト plugin（`locations=db/migration` のみ）では走らない |

## 命名規約

- `V<n>__<description>.sql`：バージョン migration。一度適用したら **編集禁止**
- `R__<description>.sql`：Repeatable migration。チェックサムが変わると再適用される
- 本ボイラープレートでは **シードのみ R\_\_ を使う**（dev_seed）。本番には絶対適用しないため `db/dev-bootstrap/` ディレクトリで分離

## VSCode タスク

| タスク           | コマンド                                                                  | 用途                          |
| ---------------- | ------------------------------------------------------------------------- | ----------------------------- |
| `flyway:migrate` | `mvn flyway:migrate`                                                      | 通常マイグレーション（冪等）  |
| `flyway:info`    | `mvn flyway:info`                                                         | 適用済み migration の状態確認 |
| `flyway:reset`   | `mvn flyway:clean flyway:migrate -Dflyway.locations=...seed,...migration` | 全削除 → 再構築 → シード投入  |

`flyway:reset` は **`inputs.promptString`** で `yes` 入力を必須化し、誤実行を防ぐ。

## 運用上の注意

- 本番運用では `flyway.cleanDisabled=true` を設定する想定（適用先プロジェクトで対応）
- `db/dev-bootstrap/R__dev_seed.sql` が本番 location に紛れ込まないこと（Maven `<locations>` で明示分離。デフォルト plugin は `db/migration` のみ）
- **`db/dev-bootstrap` は専用の履歴テーブル `flyway_schema_history_dev_bootstrap` で管理する**（`-Pe2e` の `e2e-flyway-migrate-dev-bootstrap` 実行で `<table>` を上書き）。アプリ所有スキーマ（`db/migration`, 履歴 `flyway_schema_history`）とバージョン系譜を分離するため。将来ここに社内ライブラリ共通 DB の開発用 DDL（ライブラリ側が独自採番した `V*`）を足しても、アプリの `V*` と同一履歴に束ねないので `Found more than one migration with version` の衝突が起きない
- **同実行には `baselineOnMigrate=true` + `baselineVersion=0` を付ける**。app schema 実行が先に `task` 等を作るため dev-bootstrap 実行時点で schema は非空だが、専用履歴テーブルはこの実行で初めて作られる → Flyway の安全ガード「非空スキーマ かつ 履歴テーブル無しの migrate は拒否」に掛かるため `baselineOnMigrate=true` で許可する。既定の `baselineVersion=1` だと共通ライブラリの `V1` が baseline 以下でスキップされるため `baselineVersion=0` を明示する
- V スクリプトを編集すると Flyway がチェックサム不一致でエラー化する。`V<n+1>__fix_*.sql` で追補すること
- **既存 DB で `R__dev_seed.sql` を `db/migration` → `db/dev-bootstrap` へ移設した直後は `flyway:repair` が必要**。移設前に `dev seed` を適用済みの DB（ローカル postgres など）では、デフォルト `flyway:migrate` が `Detected applied migration not resolved locally: dev seed` で validate 失敗する（既定履歴テーブルに記録が残るが location から消えたため）。`bash scripts/with-env.sh ./mvnw flyway:repair` で履歴を整合させると解消する。E2E H2 は `mvn clean` で毎回まっさらなため対象外。この移設変更を pull した各開発者も一度だけ `flyway:repair` を実行すること
