# Flyway

PostgreSQL のスキーマ管理は Flyway で行う。バージョン migration（本番・開発共通）と開発用シード（reset 時のみ）を分離する。

## ディレクトリ構成

| 種別                 | パス                                          | 用途                                                                 |
| -------------------- | --------------------------------------------- | -------------------------------------------------------------------- |
| バージョン migration | `src/main/resources/db/migration/V<n>__*.sql` | 本番・開発共通                                                       |
| 開発用シード         | `src/main/resources/db/seed/R__dev_seed.sql`  | `flyway:reset` 時のみ実行（`flyway:migrate` 通常実行では走らせない） |

## 命名規約

- `V<n>__<description>.sql`：バージョン migration。一度適用したら **編集禁止**
- `R__<description>.sql`：Repeatable migration。チェックサムが変わると再適用される
- 本ボイラープレートでは **シードのみ R\_\_ を使う**（dev_seed）。本番には絶対適用しないため `seed/` ディレクトリで分離

## VSCode タスク

| タスク           | コマンド                                                                  | 用途                          |
| ---------------- | ------------------------------------------------------------------------- | ----------------------------- |
| `flyway:migrate` | `mvn flyway:migrate`                                                      | 通常マイグレーション（冪等）  |
| `flyway:info`    | `mvn flyway:info`                                                         | 適用済み migration の状態確認 |
| `flyway:reset`   | `mvn flyway:clean flyway:migrate -Dflyway.locations=...seed,...migration` | 全削除 → 再構築 → シード投入  |

`flyway:reset` は **`inputs.promptString`** で `yes` 入力を必須化し、誤実行を防ぐ。

## 運用上の注意

- 本番運用では `flyway.cleanDisabled=true` を設定する想定（適用先プロジェクトで対応）
- `db/seed/R__dev_seed.sql` が本番 location に紛れ込まないこと（Maven `<locations>` で明示分離）
- V スクリプトを編集すると Flyway がチェックサム不一致でエラー化する。`V<n+1>__fix_*.sql` で追補すること
