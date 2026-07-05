# CI / CD

`.gitlab-ci.yml` と `.gitlab/ci/*.yml` で stage 分割した GitLab CI/CD を採用。MR パイプラインと main パイプラインの 2 系統。

## パイプライン構成

| stage    | 主な job                                                                                  |
| -------- | ----------------------------------------------------------------------------------------- |
| `lint`   | Spotless / Checkstyle blocking / Checkstyle google / PMD blocking / PMD full / Secretlint |
| `test`   | 単体 + 結合 + ArchUnit + JaCoCo（フラット 85% hard gate）                                 |
| `build`  | WAR ビルド + Docker イメージビルド + SBOM 生成 + 脆弱性スキャン                           |
| `e2e`    | Playwright golden path（MR / main で走る）                                                |
| `perf`   | k6 スモーク（main / nightly のみ）                                                        |
| `report` | `mvn site` で `/reports/` 配下を生成                                                      |
| `pages`  | docusaurus build + `/reports/` を GitLab Pages にデプロイ                                 |

## MR パイプライン

- `lint` / `test` / `build` / `e2e` を実行
- `report` は実行するが GitLab Pages デプロイは行わない（main のみ）

## main パイプライン

- 全 stage を実行
- `pages` で docusaurus サイト + `/reports/` を公開

## 品質ゲートの構成要素

- ArchUnit テスト（`test` stage）— 層境界 hard gate
- JaCoCo 閾値（`test` stage）— 本体 LINE/BRANCH 85% フラット、`e2e` stage で `-Pe2e` の LINE/BRANCH 95% に引き上げ（詳細は ADR-004）
- Playwright E2E（`e2e` stage）— タスク作成→一覧確認→完了、および業務エラー3経路（バリデーション/回復可/回復不可）の遷移確認
- k6 スモーク（`perf` stage）— 一覧 GET、VUs=5、duration=30s

## ローカルで CI を再現

- pre-commit：lint-staged で差分ファイルに対し Spotless / Checkstyle blocking / Checkstyle google / PMD blocking / PMD full の 5 段
- pre-push：`mvn -Pfast verify` で 単体 + 結合 + ArchUnit + JaCoCo
- 詳細は `rules/README.md` を参照
