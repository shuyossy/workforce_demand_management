# サンプルアプリ削除ガイド

本ボイラープレートには Jakarta EE / WildFly / PrimeFaces の使い方を示すための認証不要のタスク管理サンプル（`task`：一覧・作成・完了）が同梱されている。適用先プロジェクトでは本ガイドの手順でサンプルを撤去し、共通基盤（ロガー / 例外 / 設定 / 品質ゲート / CI/CD / ドキュメント枠組み）だけを残した状態で自案件の開発を始められる。**本サンプルは認証機能を持たない**（ログイン画面・セッション認証は存在しない）ため、認証の要否は適用先プロジェクトの要件に応じて別途検討すること。

## このガイドの目的

- サンプル（task）固有の Java コード・テスト・xhtml・SQL・ドキュメント・メッセージ ID を削除して、共通基盤のみが残った「骨格」状態にする
- 削除後に `./mvnw -Pfast clean verify` と docusaurus build が green になることを保証し、開発開始の足場を提供する
- どの資源が「サンプル削除対象」でどれが「再利用する骨格」かを明示する

## 事前準備

1. ベースラインで全体ビルドが通ることを確認:
   ```bash
   scripts/with-env.sh ./mvnw -Pfast clean verify
   cd docs && npm run build && cd ..
   ```
2. 作業ブランチを切る:
   ```bash
   git checkout -b feature/strip-sample
   ```

## 削除対象 1: Java 本体コード

```bash
rm -rf src/main/java/jp/mufg/it/rcb/domain
rm -rf src/main/java/jp/mufg/it/rcb/application
rm -rf src/main/java/jp/mufg/it/rcb/adapter/out
rm src/main/java/jp/mufg/it/rcb/adapter/in/web/TaskFormBean.java
rm src/main/java/jp/mufg/it/rcb/adapter/in/web/TaskListBean.java
```

`adapter/in/` 配下は上記 2 ファイルの削除で空になる（`LoginBean` 等の共通基盤 Bean は存在しない。本サンプルは認証機能を持たないため）。`adapter/out/persistence/` 全体は task サンプル専用なのでフォルダごと削除して構わない（適用先で新規 Repository Adapter を追加する際にフォルダを再生成する）。

`application/port/out/ClockPort.java` と `shared/web/SystemClockAdapter.java` は時刻取得の汎用 port/adapter ペアであり、`domain` / `application` 削除に含めて構わないが、案件で時刻取得の抽象化が必要であれば削除せず残してもよい（任意）。

```bash
# ClockPort とペアの SystemClockAdapter も削除する場合
rm src/main/java/jp/mufg/it/rcb/shared/web/SystemClockAdapter.java
```

## 削除対象 2: テストコード

```bash
rm -rf src/test/java/jp/mufg/it/rcb/domain
rm -rf src/test/java/jp/mufg/it/rcb/application
rm src/test/java/jp/mufg/it/rcb/adapter/in/web/TaskFormBeanIT.java
rm src/test/java/jp/mufg/it/rcb/adapter/in/web/TaskListBeanIT.java
rm src/test/java/jp/mufg/it/rcb/adapter/out/persistence/TaskRepositoryAdapterIT.java
rm src/test/java/jp/mufg/it/rcb/adapter/out/persistence/TaskMapperTest.java

# SystemClockAdapter を削除した場合は対応するテストも削除
rm src/test/java/jp/mufg/it/rcb/shared/web/SystemClockAdapterTest.java
```

`architecture.ArchitectureTest` と `shared/` 配下の他のテストは残す（→ §残るスモークテスト 参照）。

## 削除対象 3: リソース・xhtml

```bash
rm src/main/resources/db/migration/V1__init.sql
rm src/main/resources/db/dev-bootstrap/R__dev_seed.sql
rm -rf src/main/webapp/tasks
```

`db/migration/` フォルダ自体は残し、適用先で `V1__init.sql` 等を新規追加する。

## 削除対象 4: メッセージ ID

`src/main/resources/messages.properties` からタスク管理サンプル用の以下を手動削除する。共通基盤側（`RCB00001-E` の DB アクセス失敗、`MST*` 全体）は残す。

```properties
# 削除する行
RCB00001-I=タスクを作成しました（id={0}）
RCB00002-I=タスクを完了しました（id={0}）
```

「業務イベント (INFO)」セクション先頭に書いた「タスク管理サンプル（task）用。」のコメント行も削除する。

## 削除対象 5: ドキュメント

`docs/docs/04-domain-design/sample/` と `docs/docs/03-adrs/ADR-002-snapshot-vs-port.md` は既に存在しない（本ボイラープレートには同梱していない）。混在ドキュメントについては、ファイル内の `### サンプル例` セクションや `:::note サンプル例` バナーを目印に該当ブロックを削除する。対象:

- `docs/docs/04-domain-design/01-domain-model.md` の §サンプル例
- `docs/docs/04-domain-design/02-usecases.md` の §サンプル例
- `docs/docs/04-domain-design/03-business-rules.md` の §サンプル例: ルール一覧
- `docs/docs/05-technical-design.md` の §サンプル例（クラス配置 / テーブル定義 / 採番）/ バッキングビーン IT パターン集の「（サンプル）」注釈
- `docs/docs/03-adrs/ADR-001-clean-hexagonal.md` の §サンプル例
- `docs/docs/intro.md` の「次のステップ」（サンプル削除ガイドへの導線）
- `docs/docs/02-requirements/functional.md` / `docs/docs/02-requirements/non-functional.md`（題材・ユースケース・受け入れ基準を案件のものへ差し替え）

## 修正必要箇所 1: task サンプル前提のリンク・タイトル・メニュー

サンプルを削除しただけでは骨格に「task 前提のリンク・タイトル・メニュー」が残ってしまう。以下を手動修正する。

- `src/main/webapp/WEB-INF/templates/main.xhtml` の `<title>` を案件のアプリ名に置換し、`<p:menuitem>` 2 行（タスク一覧 / 新規作成）を案件のメニューに置換または削除
- `src/main/webapp/WEB-INF/web.xml` の `<welcome-file>tasks/list.xhtml</welcome-file>` を案件の初期表示画面に置換
- `README.md` の §「サンプルアプリ（適用先で削除）」セクション全体を削除し、ディレクトリ構造図の `domain/, application/, adapter/` の説明文を案件向けに更新
- `docs/sidebars.js` から `getting-started/strip-sample` の entry を削除するかどうかを検討（骨格化完了後は不要になるため削除推奨）

## 修正必要箇所 2: プロジェクト名のリネーム

本ボイラープレートは適用先プロジェクトごとに名称を変更する前提で `pom.xml` / `package.json` / `Dockerfile` / `.vscode/launch.json` 等にプロジェクト名（現在は `rcb`）が埋め込まれている。適用先の正式なプロジェクト名に置換すること。WAR ファイル名・Docker タグ・VSCode デバッグ表示名・docusaurus ページタイトル等にボイラープレート名が露出し続けるのを防ぐ。

| 対象ファイル                                      | 箇所 / フィールド                            | 現在の値                        | 置換要否                    |
| ------------------------------------------------- | -------------------------------------------- | ------------------------------- | --------------------------- |
| `pom.xml`                                         | `<artifactId>` / `<name>`                    | `rcb`                           | 必須（WAR 名に影響）        |
| `package.json` (ルート)                           | `name`                                       | `rcb`                           | 必須                        |
| `docs/package.json`                               | `name`                                       | `rcb-docs`                      | 必須                        |
| `tests/e2e/package.json`                          | `name`                                       | `rcb-e2e`                       | 必須                        |
| `Dockerfile`                                      | COPY 元 WAR 名                               | `/app/target/rcb-*.war`         | 必須（pom と連動）          |
| `.vscode/launch.json`                             | `projectName`（2 箇所）                      | `rcb`                           | 必須（wildfly:redeploy 用） |
| `src/main/webapp/WEB-INF/web.xml`                 | `<display-name>`                             | `rcb (jakartaEE)`               | 推奨                        |
| `README.md`                                       | タイトル / Docker build・run・trivy コマンド | `rcb`                           | 推奨                        |
| `docs/docs/intro.md`                              | ページタイトル                               | `rcb`                           | 推奨                        |
| `docs/docs/01-getting-started/troubleshooting.md` | WAR パス参照                                 | `target/rcb-0.0.1-SNAPSHOT.war` | 推奨                        |
| `docs/docs/06-operations/docker.md`               | docker build コマンド                        | `rcb:latest`                    | 推奨                        |

リネーム後、漏れがないか以下の grep で確認する（サンプル削除と無関係な箇所にプロジェクト名が残っていないこと）:

```bash
grep -rn "rcb" \
  --include="*.xml" --include="*.json" --include="*.md" \
  --include="*.xhtml" --include="Dockerfile" --include="*.yml" \
  . | grep -v node_modules | grep -v target | grep -v .git
```

### 変更してはいけないもの

以下は共通基盤として固定されており、リネーム対象外。

- **Java パッケージ名 `jp.mufg.it.rcb.*`** — 社内ライブラリ準拠 IF パッケージ。詳細はリポジトリ直下の `AGENTS.md` §「社内ライブラリ準拠 IF パッケージ（変更禁止）」を参照。`pom.xml` の JaCoCo include/exclude、`rules/*.xml` の Checkstyle / PMD / SpotBugs 除外パターン、`wildfly/cli/03-logging.cli` のロガー名もこのパッケージ名に依存しているため触らない
- **データソース名 `PostgresDS`** — `persistence.xml` / `wildfly/cli/02-datasource.cli` 間で結合された固定値
- **メッセージ ID プレフィックス `RCB` / `MST`** — 共通基盤メッセージとの衝突回避のため固定

## 残るスモークテスト

サンプル削除後も以下のテストは残り、`./mvnw -Pfast clean verify` で骨格の健全性を検証する hard gate として機能する。

| カテゴリ           | テストクラス                                                  | 役割                                             |
| ------------------ | ------------------------------------------------------------- | ------------------------------------------------ |
| アーキテクチャ境界 | `architecture.ArchitectureTest`                               | ArchUnit による層境界 hard gate（汎用 7 ルール） |
| 共通基盤（単体）   | `shared.config.AppConfigTest`                                 | MicroProfile Config ラッパー                     |
| 共通基盤（単体）   | `shared.web.AppUrlBuilderTest`                                | 外部公開 URL ビルダ                              |
| ログフォーマッタ   | `log.formatter.{RcbLogFormatterTest, RcbMessageResolverTest}` | messages.properties 経由のメッセージ解決         |

これらが green であれば、骨格（ArchUnit / 設定 / URL 構築 / ロガー）は正常に動作している。

## 削除後の検証

```bash
# 単体 + 結合
scripts/with-env.sh ./mvnw -Pfast clean verify

# E2E は task 用シナリオが含まれるため一時的に skip する設定が必要
# （tests/e2e/tests/golden-path.spec.ts と error-handling.spec.ts は task 前提）
# 適用先で新規 E2E シナリオを書き直したら復活させる

# docusaurus
cd docs && npm run build && cd ..
```

`tests/e2e/tests/*.spec.ts` は task のゴールデンパスと例外処理シナリオなので、削除して新規シナリオを書き直すか、案件のシナリオに書き換える。

## 次の一歩

骨格が green になったら、以下の順で自案件のドメインを構築する。

1. `docs/docs/04-domain-design/01-domain-model.md` の §記載方法の雛形 をコピーして案件ドメインを記述
2. `docs/docs/04-domain-design/02-usecases.md` の §記載方法の雛形 をコピーして案件ユースケースを記述
3. `docs/docs/04-domain-design/03-business-rules.md` の §ルール一覧（テンプレ）に案件のビジネスルールを追記
4. `src/main/java/jp/mufg/it/rcb/{domain,application,adapter}/` 配下に案件のクラスを追加
5. `src/main/resources/db/migration/V1__init.sql` を案件のテーブル定義で作成
6. `src/main/resources/messages.properties` に案件のメッセージ ID を追加（採番ルールは [技術設計書](../technical-design) §3 メッセージ ID 採番 参照）
7. `src/main/webapp/` に案件の xhtml を追加

ArchUnit / 静的解析 hard gate が pre-commit / pre-push で常時走るため、層境界違反やコード品質低下はすぐに検出される。
