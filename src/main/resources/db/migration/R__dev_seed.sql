-- 開発・E2E 用のシードデータ。
-- Flyway 標準ロケーション (db/migration) 配下の Repeatable migration として配置するため、
-- ローカル PG / E2E H2 / CI のいずれでも `flyway:migrate` 一発で投入される。
-- DELETE FROM で冪等性を確保しているため、Flyway が R__* の content hash 変化を検知して
-- 再実行しても整合性を保つ。
-- IT (JpaTestSupport.bootstrapH2) は V1 のみを classpath から直接 execute するため、
-- 本 seed は IT には影響しない。
-- 業務的に網羅したいテストデータ:
--   - PENDING: 申請者本人視点の "未処理" 一覧確認、承認/却下シナリオの起点
--   - APPROVED: 承認済みデータの表示と判定者情報の整合確認
--   - REJECTED: 却下経路の表示と判定コメントの確認

DELETE FROM leave_request;

INSERT INTO leave_request
  (applicant_emp_num, applicant_name, applicant_org_id, applicant_org_name,
   leave_type, start_date, end_date, reason, status, applied_at,
   judge_emp_num, judge_name, judged_at, judge_comment)
VALUES
  ('E0001','山田 太郎','ORG-001','リテール開発部 第一課',
   'PAID','2026-06-01','2026-06-03','私用のため','PENDING','2026-05-20T09:00:00',
   NULL,NULL,NULL,NULL),
  ('E0004','高橋 三郎','ORG-001','リテール開発部 第一課',
   'SPECIAL','2026-07-10','2026-07-10','家族行事','PENDING','2026-05-21T10:00:00',
   NULL,NULL,NULL,NULL),
  ('E0001','山田 太郎','ORG-001','リテール開発部 第一課',
   'COMPENSATORY','2026-04-15','2026-04-15','振替休暇','APPROVED','2026-04-10T13:00:00',
   'E0002','佐藤 花子','2026-04-11T09:00:00','OK'),
  ('E0004','高橋 三郎','ORG-001','リテール開発部 第一課',
   'PAID','2026-05-01','2026-05-02','私用のため','REJECTED','2026-04-20T10:00:00',
   'E0002','佐藤 花子','2026-04-22T11:30:00','繁忙期のため別日程で再申請してください');
