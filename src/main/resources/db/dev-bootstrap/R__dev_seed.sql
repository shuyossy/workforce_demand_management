-- 開発・E2E 用のシードデータ（本番には絶対に投入しない）。
-- 本番共通の location (db/migration) から分離し、db/dev-bootstrap 配下に配置する。
-- この location は -Pe2e の専用 flyway 実行 e2e-flyway-migrate-dev-bootstrap でのみ流れ、
-- アプリ所有スキーマとは別の履歴テーブル flyway_schema_history_dev_bootstrap で管理される。
-- env 駆動のデフォルト flyway-maven-plugin（本番/ローカル共用・locations=db/migration のみ）では走らない。
-- 履歴を分離しているため、将来この配下に社内ライブラリ共通 DB の開発用 DDL (V*) を足しても
-- アプリ所有スキーマの V* とバージョン系譜が衝突しない。
-- Repeatable migration のため、DELETE FROM で冪等性を確保する（content hash 変化で再実行されても整合を保つ）。
-- IT (JpaTestSupport.bootstrapH2) は V1 のみを classpath から直接 execute するため、
-- 本 seed は IT には影響しない。
-- 一覧・完了シナリオの起点として TODO を数件、表示確認用に DONE を 1 件投入する。

DELETE FROM task;

INSERT INTO task (title, status, created_at, completed_at)
VALUES
  ('要件定義レビューの準備', 'TODO', '2026-07-01T09:00:00', NULL),
  ('サンプルアプリの動作確認', 'TODO', '2026-07-02T10:30:00', NULL),
  ('キックオフ議事録の作成', 'DONE', '2026-06-30T14:00:00', '2026-06-30T15:00:00');
