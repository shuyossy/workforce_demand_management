-- 開発・E2E 用のシードデータ。
-- Flyway 標準ロケーション (db/migration) 配下の Repeatable migration として配置するため、
-- ローカル PG / E2E H2 / CI のいずれでも `flyway:migrate` 一発で投入される。
-- DELETE FROM で冪等性を確保しているため、Flyway が R__* の content hash 変化を検知して
-- 再実行しても整合性を保つ。
-- IT (JpaTestSupport.bootstrapH2) は V1 のみを classpath から直接 execute するため、
-- 本 seed は IT には影響しない。
-- 一覧・完了シナリオの起点として TODO を数件、表示確認用に DONE を 1 件投入する。

DELETE FROM task;

INSERT INTO task (title, status, created_at, completed_at)
VALUES
  ('要件定義レビューの準備', 'TODO', '2026-07-01T09:00:00', NULL),
  ('サンプルアプリの動作確認', 'TODO', '2026-07-02T10:30:00', NULL),
  ('キックオフ議事録の作成', 'DONE', '2026-06-30T14:00:00', '2026-06-30T15:00:00');
