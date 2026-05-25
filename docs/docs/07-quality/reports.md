# レポート

`./mvnw site` で `target/site/` 配下に各種品質レポートを生成し、GitLab Pages 経由で `/reports/` パスに公開する。

## 生成されるレポート

| レポート          | パス                                 | 内容                                                       |
| ----------------- | ------------------------------------ | ---------------------------------------------------------- |
| Checkstyle        | `target/site/checkstyle.html`        | コーディング規約違反一覧                                   |
| Checkstyle google | `target/site/checkstyle-google.html` | Google Java Style 違反一覧                                 |
| PMD               | `target/site/pmd.html`               | バグ可能性 / コードスメル一覧                              |
| SpotBugs          | `target/site/spotbugs.html`          | バイトコード解析の警告一覧                                 |
| Surefire          | `target/site/surefire-report.html`   | 単体テスト結果                                             |
| Failsafe          | `target/site/failsafe-report.html`   | 結合テスト結果                                             |
| JaCoCo            | `target/site/jacoco/index.html`      | カバレッジレポート（フラット閾値: 本体 85% / `-Pe2e` 95%） |

## GitLab Pages デプロイ

`.gitlab-ci.yml` の `pages` job で：

1. docusaurus サイトを `docs/build/` に生成
2. `mvn site` で `target/site/` を生成
3. `target/site/` を `public/reports/` にコピー
4. `docs/build/` を `public/` 直下にコピー
5. `public/` を GitLab Pages として公開

公開後の URL 例：

- ドキュメント：`https://<group>.gitlab.io/<project>/`
- レポート：`https://<group>.gitlab.io/<project>/reports/jacoco/index.html`

公開設定の詳細は `.gitlab/ci/pages.yml` を参照。
