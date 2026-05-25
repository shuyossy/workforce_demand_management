# Java 品質担保戦略（rules/ ガイド）

このドキュメントは、本ボイラープレートにおける **Java の品質担保戦略** を初心者にも分かる形で説明するためのものです。どの開発サイクルのフェーズで、どのツールが、何をチェックして、何をチェックしないのか、を網羅的にまとめます。

`rules/` 配下の設定ファイル（Checkstyle / PMD / SpotBugs）と、それを呼び出す `pom.xml` / `.husky/` / `.gitlab/ci/` / `scripts/` / `.vscode/` が本戦略の実装です。

---

## 1. 大局観：シフトレフトの 4 階層

バグ・脆弱性・スタイル逸脱を **リモートに持ち出さず、デプロイもさせない** ことが目的です。同じ観点を複数のフェーズで段階的に強化します。

```
[ Editor ]  保存するたびに自動整形（ヒューマンフィードバックを最速にする）
     ↓
[ pre-commit ]  変更ファイルだけを軽量チェック（コミットを壊さない、ただし致命は止める）
     ↓
[ pre-push ]  リポジトリ全体を完全静的解析 + ユニットテスト（リモート push 前の最終検問）
     ↓
[ CI (MR / main) ]  pre-push と同等 + カバレッジ閾値 + SCA + 秘密情報 + イメージスキャン
```

- **後ろのフェーズは前のフェーズを内包する**（例: pre-push に通ったコードは、CI でも pre-push と同じ項目を再検証される）。
- **前のフェーズは後ろのフェーズの 部分集合 ＋ 速度重視**（例: pre-commit は変更ファイルだけを対象に、致命的パターンだけ走らせる）。

---

## 2. フェーズ × ツール 責務マトリクス（早見表）

以下の記号で集約します：

- ✅ 実行し **違反で build/commit/push/CI を失敗させる**（hard gate）
- ➖ 実行しない

AI 主導開発の方針として、Google Java Style 全規則と PMD 全カテゴリの両方を **コミット時点から hard gate** として強制する。シフトレフトの効果を最大化するため、soft gate（情報表示のみ）の概念は採用しない。重大な欠陥（フォーマット崩れ / 致命的バグ / セキュリティ / 依存 CVE / カバレッジ未達）はもちろん、Google Java Style からのあらゆる逸脱も ✅ で確実に止める。

| 観点                                   | ツール                                               | Editor      | pre-commit              | pre-push              | CI MR                      | CI main |
| -------------------------------------- | ---------------------------------------------------- | ----------- | ----------------------- | --------------------- | -------------------------- | ------- |
| フォーマット適用                       | google-java-format (jar)                             | ➖          | ✅ (差分 in-place)      | ➖                    | ➖                         | ➖      |
| フォーマット適用                       | VSCode redhat.java + eclipse-formatter.xml           | ✅ (保存時) | ➖                      | ➖                    | ➖                         | ➖      |
| フォーマット差分検知                   | Spotless Maven Plugin                                | ➖          | ➖                      | ✅ (`spotless:check`) | ✅                         | ✅      |
| 致命的スタイル/コード（hard gate）     | Checkstyle (`checkstyle-blocking.xml`)               | ➖          | ✅ (差分 / pre-commit)  | ✅ (全体 / `verify`)  | ✅ (`checkstyle:check`)    | ✅      |
| Google Java Style 全規則（hard gate）  | Checkstyle (`checkstyle-google.xml` = google_checks) | ➖          | ✅ (差分 / pre-commit)  | ✅ (全体 / `verify`)  | ✅ (`checkstyle:check`)    | ✅      |
| 致命的バグ・誤用                       | PMD (`ruleset-blocking.xml`)                         | ➖          | ✅ (差分)               | ➖                    | ➖                         | ➖      |
| PMD 7.x 標準全カテゴリ                 | PMD (`ruleset.xml`)                                  | ➖          | ✅ (差分 / pre-commit)  | ✅ (`verify`)         | ✅ (`pmd:check`)           | ✅      |
| バイトコード解析（バグ＋セキュリティ） | SpotBugs + FindSecBugs                               | ➖          | ➖                      | ✅ (`verify`)         | ✅ (`spotbugs:check`)      | ✅      |
| ユニットテスト                         | JUnit (surefire)                                     | ➖          | ➖                      | ✅ (`test`)           | ✅                         | ✅      |
| カバレッジ計測（レポート生成のみ）     | JaCoCo report                                        | ➖          | ➖                      | ✅                    | ✅                         | ✅      |
| カバレッジ閾値チェック                 | JaCoCo check (ci-mr profile)                         | ➖          | ➖                      | ➖                    | ✅ (LINE≥60% / BRANCH≥50%) | ✅      |
| 依存脆弱性 (Java)                      | OWASP Dependency-Check (ci-mr profile)               | ➖          | ➖                      | ➖                    | ✅ (CVSS≥7 で fail)        | ✅      |
| 依存脆弱性 (FS / JS)                   | Trivy fs                                             | ➖          | ➖                      | ➖                    | ✅ (HIGH/CRITICAL)         | ✅      |
| 秘密情報（ファイル内容）               | Secretlint                                           | ➖          | ✅ (差分含む全ファイル) | ➖                    | ➖                         | ➖      |
| 秘密情報（Git 履歴）                   | Gitleaks                                             | ➖          | ➖                      | ➖                    | ✅                         | ✅      |
| コミットメッセージ                     | Commitlint                                           | ➖          | ✅ (commit-msg hook)    | ➖                    | ➖                         | ➖      |
| Dockerfile lint                        | Hadolint                                             | ➖          | ➖                      | ➖                    | ✅                         | ✅      |
| Container Image 脆弱性                 | Trivy image                                          | ➖          | ➖                      | ➖                    | ✅                         | ✅      |
| SBOM 生成                              | Syft (CycloneDX)                                     | ➖          | ➖                      | ➖                    | ✅                         | ✅      |

> 参考値：pre-commit ≈ 15 秒、pre-push ≈ 1〜3 分、CI パイプライン全体 ≈ 数分〜十数分（キャッシュ有無による）。

---

## 3. フェーズ別の詳細

### 3.1 Editor（VSCode）

実装：`.vscode/settings.json`

| 設定                                                               | 効果                                                        |
| ------------------------------------------------------------------ | ----------------------------------------------------------- |
| `editor.formatOnSave: true`                                        | 保存時に自動整形                                            |
| `"[java]": { "editor.defaultFormatter": "redhat.java" }`           | Java は Red Hat 拡張で整形                                  |
| `java.format.settings.url: rules/checkstyle/eclipse-formatter.xml` | Google Java Style 準拠の Eclipse JDT フォーマッタ設定を強制 |

- **見る**：ファイル保存時のフォーマット適用。
- **見ない**：ロジックのバグ、スタイル以外のコード品質、セキュリティ。拡張を入れていない開発者にはそもそも効かない（強制力なし）。
- **狙い**：ヒューマンフィードバックを最速で返し、pre-commit 以降で「ほぼ問題にならない」状態を目指す。

> 前提拡張機能は [ルート README の "VSCode 拡張機能" 節](../README.md#vscode-拡張機能推奨動作前提) を参照。

### 3.2 pre-commit（`.husky/pre-commit` → `npx lint-staged`）

`package.json` の `lint-staged` 定義が本体です。**変更ファイルだけ**を対象にします。Jakarta EE ブランチでは AI 主導開発の方針として、Google Java Style 全規則と PMD 全カテゴリも差分単位の hard gate に持ち上げ、合計 5 段でコミット時点での網羅検知を行います。

```jsonc
"lint-staged": {
  "*.java": [
    "node scripts/run-spotless-staged.mjs",                       // 1) google-java-format で in-place 整形
    "node scripts/run-checkstyle.mjs --ruleset=blocking",          // 2) Checkstyle blocking ruleset (致命パターン)
    "node scripts/run-checkstyle.mjs --ruleset=google",            // 3) Checkstyle google_checks (全規則 hard gate)
    "node scripts/run-pmd.mjs --ruleset=blocking",                 // 4) PMD blocking ruleset (致命パターン)
    "node scripts/run-pmd.mjs --ruleset=full"                      // 5) PMD 全カテゴリ (hard gate)
  ],
  "*.{js,mjs,cjs}": ["prettier --write", "eslint --fix --max-warnings=0"],
  "*.{json,md,yml,yaml}": ["prettier --write"],
  "*": ["secretlint"]
}
```

順序の意図：Spotless で auto-fix できるフォーマット系を最初に処理 → Checkstyle / PMD の致命パターン（blocking）→ Google 全規則（google）／ PMD 全カテゴリ（full）。1〜2 のステップで吸収できる違反を先に消すことで、3〜5 で残るのは「Spotless が修正できない構造的な逸脱」だけになる。

**Java に関して**：

- **1) Spotless (google-java-format 1.22.0)**：
  - 見る：変更ファイルのフォーマット。
  - 動作：違反があれば **その場で in-place 修正**し、lint-staged が自動で再 stage する（コミットを止めない）。
  - 見ない：スタイル以外のコード品質。
  - pre-commit で差分走査にするため、Maven プラグインではなく jar を直接呼ぶ（`scripts/run-spotless-staged.mjs`）。

- **2) Checkstyle blocking（`rules/checkstyle/checkstyle-blocking.xml`）**：
  - 見る：以下の「議論の余地がない致命パターン」のみ（severity=error）。
    - 行末空白（`RegexpSingleline`）
    - Tab 文字（`FileTabCharacter`, `eachLine=true`）
    - EOF 改行抜け（`NewlineAtEndOfFile`）
    - 未使用 import / 重複 import / `*` import
    - 空 catch ブロック（`EmptyCatchBlock`, 変数名 `expected` は意図的 no-op として許容）
    - `@Override` 抜け
    - `equals()` と `hashCode()` 不整合
    - 冗長な boolean 式 / 文字列リテラルの `==` 比較
    - `public static final int` のような修飾子順序違反
    - 行末空白 / Tab / EOF 改行の 3 点は Spotless (google-java-format) が常に修正するため平時は冗長だが、`git commit --no-verify` で Spotless をバイパスされた際の最終保険として pre-push / CI で意味を持つので重複して残している。
  - 見ない：Google Java Style 全般（それは別ファイルの `checkstyle-google.xml` が pre-commit 差分 / pre-push / CI で同じく hard gate として網羅する）。
  - スコープを絞る理由：pre-commit は高速であるべきで、かつ「まだ書きかけ」の微調整的違反で止めたくないため。
  - **同じ blocking ruleset は pre-push / CI でも再実行される**（`pom.xml` の `maven-checkstyle-plugin` / `checkstyle-blocking` execution）。pre-commit を `--no-verify` でバイパスした変更や、差分以外に混入した違反はここで必ず捕捉する。

- **3) Checkstyle google（`rules/checkstyle/checkstyle-google.xml`）**：
  - 見る：Google Java Style Guide の全規則（350+ ルール）。命名規約 / インデント / JavaDoc / import 整列 / 括弧の位置 等を網羅。
  - 動作：違反で **コミット停止（hard gate）**。Jakarta EE ブランチでは AI 主導開発の方針上、コミット時点で Google Style 全規則を強制する。
  - 見ない：実行時バグ、スレッドセーフティ、セキュリティ（それらは PMD / SpotBugs / OWASP の責務）。
  - 同一 ruleset を pre-push / CI でも全体対象で再実行する（`git commit --no-verify` バイパス対策、差分外への混入対策）。

- **4) PMD blocking（`rules/pmd/ruleset-blocking.xml`）**：
  - 見る：errorprone カテゴリから厳選した 6 種 + `CloseResource` / `EmptyControlStatement`。具体的には `AvoidCatchingThrowable` / `EmptyCatchBlock` / `EmptyControlStatement` / `AvoidBranchingStatementAsLastInLoop` / `CloseResource` / `DontImportSun` / `UseEqualsToCompareStrings`。
  - 見ない：致命パターン以外（それは PMD full で網羅）。

- **5) PMD full（`rules/pmd/ruleset.xml`）**：
  - 見る：PMD 7.x の標準 8 カテゴリ（`bestpractices`, `codestyle`, `design`, `documentation`, `errorprone`, `multithreading`, `performance`, `security`）を **そのまま** 参照。
  - 動作：違反で **コミット停止（hard gate）**。
  - 見ない：カスタムルール（定義していない）。
  - 同一 ruleset を pre-push / CI でも全体対象で再実行する。

**Java 以外（参考）**：

- **secretlint**（全ファイル対象）：API キー / トークン / 秘密鍵などのシークレットを正規表現で検出。履歴は見ない（それは CI の gitleaks の仕事）。
- **prettier / eslint / commitlint** の詳細はこのドキュメントのスコープ外（ルート README を参照）。

### 3.3 pre-push（`.husky/pre-push`）

```sh
npm run lint               # ESLint 全体
npm run format:check       # Prettier 差分検知
npm test                   # Vitest
npm run audit              # npm audit --audit-level=high
./mvnw -T 1C -Pfast verify # Java 静的解析 + テスト
```

`./mvnw -Pfast verify` は `fast` プロファイル（既定で active、`ci-mr` の OWASP DC / JaCoCo check を含まない）で以下が動作します：

| Maven フェーズ | 実行される内容                                                                                                                                  |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `validate`     | **Spotless check**（違反があれば fail）                                                                                                         |
| `test`         | JUnit (surefire) + **JaCoCo report**（閾値チェックはなし）                                                                                      |
| `verify`       | **Checkstyle check**（google + blocking の 2 execution、いずれも hard gate） / **PMD check** / **SpotBugs check**（FindSecBugs プラグイン込み） |

- **Checkstyle は 2 つの hard gate ruleset で構成される**（`pom.xml` の maven-checkstyle-plugin に 2 本の execution が登録されている）：
  - **`rules/checkstyle/checkstyle-google.xml`（google_checks 逐語コピー + severity=error 1 行パッチ / hard gate / ✅ ブロック）**
    - 見る：Google Java Style Guide の全規則（命名規約 / インデント / JavaDoc / import 整列 / 括弧の位置 等、350+ ルール）。
    - 見ない：実行時バグ、スレッドセーフティ、セキュリティ。
    - **ビルド / コミットを必ず落とす**。Checker ルート直下 `severity="error"` 化により、違反は exit 非ゼロでコミット停止。
    - Jakarta EE ブランチでは AI 主導開発の方針上、Google Java Style 全規則を hard gate として強制する設計。サンプル特化ブランチで使っていた soft gate / advisory 概念は撤去している。
    - 独自パッチは **Checker 直下 `severity` の 1 行のみ**に限定し、それ以外は逐語コピーを維持（checkstyle のバージョン更新時に最新版へ追従しやすくするため）。

  - **`rules/checkstyle/checkstyle-blocking.xml`（プロジェクト独自 / hard gate / ✅ ブロック）**
    - 見る：Google の網羅では拾えない、議論の余地がない致命パターン（行末空白・Tab・EOF 改行・未使用 import・空 catch（`expected` 変数名は許容）・`@Override` 抜け・equals & hashCode 不整合・冗長 boolean・`String` の `==` 比較・modifier 順）。
    - 見ない：Google Java Style 全般（それは `checkstyle-google.xml` が網羅）。インデント・import 並び順・WhitespaceAround などフォーマット領域は Spotless (google-java-format) が整形で担保するため blocking に重複させない（Spotless 通過後のコードに対して blocking が違反を上げるとデッドロックを生むため）。
    - **ビルド / コミットを必ず落とす**。Checker ルート直下 `severity="error"` により違反は errorCount にカウントされ、Maven でも Checkstyle CLI でも exit コードが非ゼロになる。
    - 実行経路は 2 系統：
      1. pre-commit（`scripts/run-checkstyle.mjs --ruleset=blocking` 経由）→ 変更ファイル（差分）を対象に高速に走る。
      2. pre-push / CI（`maven-checkstyle-plugin` の `checkstyle-blocking` execution 経由）→ リポジトリ全体を対象に走る。
    - 同じ blocking ruleset を pre-push/CI でも再実行することで、`git commit --no-verify` でバイパスされた変更や、差分以外に混入した違反も確実に止める。

- **PMD（`rules/pmd/ruleset.xml`）**：
  - 見る：PMD 7.x の標準 8 カテゴリ（`bestpractices`, `codestyle`, `design`, `documentation`, `errorprone`, `multithreading`, `performance`, `security`）を **そのまま** 参照。
  - 見ない：カスタムルール（定義していない）。
  - **`failOnViolation=true`** なので違反は build を失敗させる。Jakarta EE ブランチでは pre-commit でも差分対象で同一 ruleset を走らせるため、コミット時点で hard gate として機能する。
  - 除外方針：`<exclude>` を直書きせず、個別抑制は `@SuppressWarnings("PMD.XXX")` または行末 `// NOPMD - 理由` を推奨。ルールセット全体で除外が必要になった時のみ `ruleset.xml` を編集し、§4.5 の表に追記する。

- **SpotBugs + FindSecBugs**：
  - 見る：
    - SpotBugs 本体：バイトコードを解析し、null 逆参照 / リソースリーク / Equality & HashCode の不整合 / 並行性バグ 等 400+ パターン。
    - FindSecBugs プラグイン：SQLi / XSS / XXE / Path Traversal / コマンドインジェクション / 弱い暗号 / ハードコードされたパスワード・鍵 等のセキュリティ脆弱性。`pom.xml` で spotbugs-maven-plugin の `<plugins>` に `com.h3xstream.findsecbugs:findsecbugs-plugin` を宣言しているだけで、FindSecBugs の全 detector が自動的に有効になる（include リストは不要）。
  - 見ない：ソースコードの文字列パターン（行長・空白など）。スタイルは見ない。
  - 設定：`effort=Max`（最も厳密な解析）、`threshold=Low`（Low 優先度以上を全て報告）、`check` ゴール（報告があれば build fail）。
  - 誤検知の除外は `rules/spotbugs/exclude.xml` に追記する（`<excludeFilterFile>` として参照されている）。

- **JaCoCo**：
  - 見る：`report` ゴールによりカバレッジレポート（`target/site/jacoco/`）を生成。
  - 見ない：pre-push では閾値チェックをしない（`fast` profile）。遅延を避けるため、カバレッジ閾値は CI 側に寄せている。

### 3.4 CI（GitLab MR / main パイプライン）

`.gitlab-ci.yml` が `.gitlab/ci/*.yml` を include。2 系統の pipeline が走ります：

- **MR pipeline**：`setup → quality → security → package → scan`
- **main pipeline**：MR と同じ ＋ `pages`（docusaurus 公開） ＋ `release`（image push）

Java 品質担保に関連する主な job（`ci-mr` profile が有効）：

| Stage    | Job                | 実体                                                                             | 見る                                                                                                                                                                                                                                         | 見ない                                                    |
| -------- | ------------------ | -------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| quality  | `lint:format`      | `npm run format:check`                                                           | Prettier 差分                                                                                                                                                                                                                                | Java（Spotless は `lint:checkstyle` 経由で Maven が見る） |
| quality  | `lint:eslint`      | `npm run lint`                                                                   | JS のコード品質                                                                                                                                                                                                                              | Java                                                      |
| quality  | `lint:checkstyle`  | `./mvnw checkstyle:check@checkstyle-google checkstyle:check@checkstyle-blocking` | **google**（`checkstyle-google.xml` = google_checks + severity=error、Google Java Style 全規則 hard gate）＋ **blocking**（`checkstyle-blocking.xml`、プロジェクト独自致命パターン hard gate）の 2 execution を明示起動。どちらも違反で fail | ロジック / セキュリティ                                   |
| quality  | `lint:pmd`         | `./mvnw pmd:check`                                                               | PMD 全カテゴリ（`ruleset.xml`）                                                                                                                                                                                                              | バイトコード解析                                          |
| quality  | `lint:spotbugs`    | `./mvnw compile spotbugs:check`                                                  | バグ + セキュリティ（FindSecBugs 込み）                                                                                                                                                                                                      | ソース文字列パターン                                      |
| quality  | `test:java`        | `./mvnw test`                                                                    | JUnit 実行 + JaCoCo report 生成                                                                                                                                                                                                              | カバレッジ閾値（`coverage:java` が担当）                  |
| quality  | `test:js`          | `npm run test:coverage`                                                          | Vitest + coverage                                                                                                                                                                                                                            | Java                                                      |
| quality  | `coverage:java`    | `./mvnw -Pci-mr jacoco:report jacoco:check`                                      | **本体 LINE/BRANCH ≥ 85%**（未達で fail）。`-Pe2e` 系統では別 stage で **LINE/BRANCH ≥ 95%** を check                                                                                                                                        | ―                                                         |
| quality  | `secret:gitleaks`  | `gitleaks detect --source=. --no-git=false`                                      | Git 履歴含む秘密情報                                                                                                                                                                                                                         | ファイル単体のパターン（secretlint が補完）               |
| security | `sca:owasp`        | `./mvnw -Pci-mr dependency-check:check`                                          | Java 依存の既知 CVE（CVSS ≥ 7 で fail）                                                                                                                                                                                                      | JS / OS / Container                                       |
| security | `sca:trivy-fs`     | `trivy fs --severity HIGH,CRITICAL --ignore-unfixed .`                           | ファイルシステム / JS 依存                                                                                                                                                                                                                   | ソースコードのバグ                                        |
| package  | `build:image`      | `docker build` + `syft ... -o cyclonedx-json`                                    | ―                                                                                                                                                                                                                                            | ―                                                         |
| scan     | `lint:hadolint`    | `hadolint Dockerfile`                                                            | Dockerfile のベストプラクティス違反                                                                                                                                                                                                          | コンテナ内容                                              |
| scan     | `scan:trivy-image` | `trivy image --severity HIGH,CRITICAL --ignore-unfixed`                          | OS パッケージ / 最終 image 内の依存 CVE                                                                                                                                                                                                      | Dockerfile の書き方（Hadolint が担当）                    |

> 社内プライベートネットワーク下で **NVD 直接アクセスができない場合**、OWASP DC は `pom.xml` の `ci-mr` profile にコメントで示した `<nvdApiServerUrl>` / `<nvdDatafeedUrl>` / `<nvdApiKey>` で社内ミラーに切り替えてください（詳細は [ルート README § 社内プライベートネットワーク環境への適用](../README.md#社内プライベートネットワーク環境への適用)）。

### 3.5 レポート出力（`mvn site` / GitLab Pages）

`./mvnw site` を実行すると、Checkstyle / PMD / SpotBugs / JUnit / JaCoCo の HTML レポートを `target/site/` に集約生成する。hard gate で build を落とすのは §3.2〜3.4 の経路の責務であり、`mvn site` はあくまで **チームで状況を俯瞰するためのダッシュボード** という位置付け。そのため reporting 側では違反があってもサイト生成は失敗させない設定（`<pom.xml>` の `<reporting>` セクション参照）にしている。

| レポート               | ファイル               | 内容                                                                   |
| ---------------------- | ---------------------- | ---------------------------------------------------------------------- |
| プロジェクト概要       | `index.html`           | プロジェクト情報・依存・プラグイン一覧                                 |
| Checkstyle（blocking） | `checkstyle.html`      | `checkstyle-blocking.xml`（プロジェクト独自の致命パターン集合）の指摘  |
| PMD                    | `pmd.html`             | `ruleset.xml`（PMD 7.x 標準 8 カテゴリ）の指摘                         |
| SpotBugs + FindSecBugs | `spotbugs.html`        | バイトコード解析 + セキュリティ脆弱性（effort=Max / threshold=Low）    |
| JUnit                  | `surefire-report.html` | `target/surefire-reports/*.xml` のテスト結果サマリ                     |
| JaCoCo カバレッジ      | `jacoco/index.html`    | 行・分岐カバレッジ（閾値チェックは `ci-mr` profile の `jacoco:check`） |
| ソース相互参照（JXR）  | `xref/`                | Checkstyle / PMD の指摘行から該当ソースにハイパーリンクで遷移する      |

> **Checkstyle のサイト掲載は blocking ルールセットのみ**です。`maven-checkstyle-plugin` の `checkstyle` report goal は出力ファイル名が固定（`checkstyle.html`）で、複数 reportSet を宣言しても同一ファイルに上書きされるため、google（google_checks 全体 hard gate）と blocking（プロジェクト独自致命パターン hard gate）の両方をサイトに 2 ページで並べることは技術的に不可能です。サイトには「プロジェクト独自の致命パターン」のレポート（blocking）を掲載し、Google Java Style 全規則違反は `./mvnw verify` や CI ログ（`lint:checkstyle` ジョブ）で参照してください。なお両 ruleset とも hard gate なので、CI を通った時点でどちらも違反ゼロが保証されます。

#### 実行方法

```sh
# フル生成（推奨）。verify で各ツールの XML を生成してから site で集約。
./mvnw clean verify site
open target/site/index.html
```

- **Surefire レポートは `report-only` で既存 XML を読むだけ**のため、`./mvnw site` 単独ではテストサマリが空になる。必ず `verify site`（または `test site`）の形で連結すること。
- Checkstyle / PMD / SpotBugs の設定は `<build>` 側と `<reporting>` 側で重複宣言して同値にそろえている（乖離していると「CI は通ったのにサイトには違反が出る」混乱を生むため）。ルール改定時は両方を更新する。
- site ライフサイクルは default ライフサイクルと独立だが、`verify site` と連結した場合は frontend-maven-plugin（Node / npm）も回る。

#### GitLab Pages 公開

main ブランチの CI（`.gitlab/ci/pages.yml`）では、`site:java` ジョブが `./mvnw -Pci-main verify site` を実行し、後続の `pages` ジョブが生成物を `public/reports/` サブパスに合成して Docusaurus サイトと並行公開する。URL の例：

| パス                | 内容                                                    |
| ------------------- | ------------------------------------------------------- |
| `/`                 | Docusaurus によるドキュメント                           |
| `/reports/`         | Maven site のランディング（`index.html`）               |
| `/reports/pmd.html` | PMD のレポート（他のレポートも `/reports/<name>.html`） |

MR パイプラインでは `site:java` は走らない（`rules: $CI_COMMIT_BRANCH == "main"`）。MR レビュー時に各ツールの指摘を見るには、引き続き対応する `lint:*` / `test:java` / `coverage:java` ジョブのログと既存の JUnit / JaCoCo / OWASP DC の artifact を参照する。

---

## 4. ツール別「見る／見ない」まとめ（俯瞰用）

| ツール                                                           | 見る（Do）                                                                                                                                                                       | 見ない（Don't）                                                                                                                                               | カバーフェーズ                             |
| ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| google-java-format / Spotless / eclipse-formatter.xml            | フォーマット（括弧位置 / インデント / 空白 / import 並び / 末尾改行）                                                                                                            | 命名 / セマンティクス / バグ                                                                                                                                  | Editor, pre-commit, pre-push, CI           |
| Checkstyle (`checkstyle-blocking.xml` / hard gate)               | 行末空白 / Tab / EOF 改行 / 未使用 import / 空 catch（`expected` 変数名は許容） / `@Override` / equals&hashCode / 文字列 `==` / 修飾子順。**違反で必ず build / commit を落とす** | Google Java Style 全般（それは `checkstyle-google.xml` が網羅）。インデント・import 並び順・WhitespaceAround 等は Spotless と衝突するため blocking に入れない | pre-commit（差分）＋ pre-push / CI（全体） |
| Checkstyle (`checkstyle-google.xml` = google_checks / hard gate) | Google Java Style の全規則（命名 / JavaDoc / ブロック構造 など、350+ ルール）。**違反で必ず build / commit を落とす**（Checker 直下 `severity="error"` の独自 1 行パッチ）       | バイトコード / 依存 CVE / ランタイムバグ。hard gate のうちフォーマット領域は Spotless、致命パターンは `checkstyle-blocking.xml` に分担                        | pre-commit（差分）＋ pre-push / CI（全体） |
| PMD (`ruleset-blocking.xml`)                                     | 致命的な error-prone パターン（7 種）                                                                                                                                            | その他 PMD ルール                                                                                                                                             | pre-commit                                 |
| PMD (`ruleset.xml`)                                              | PMD 7.x 標準 8 カテゴリ（bestpractices / codestyle / design / documentation / errorprone / multithreading / performance / security）                                             | フォーマット微調整 / バイトコード解析 / 依存 CVE                                                                                                              | pre-commit（差分）＋ pre-push / CI（全体） |
| SpotBugs + FindSecBugs                                           | バイトコード解析によるバグパターン + セキュリティ脆弱性（SQLi/XSS/XXE/Path Traversal/Crypto/Hard-coded secrets など）                                                            | ソースコードのスタイル / 書式                                                                                                                                 | pre-push, CI                               |
| JUnit (surefire)                                                 | 開発者が書いた単体テスト                                                                                                                                                         | テストが未記述のコード                                                                                                                                        | pre-push, CI                               |
| JaCoCo                                                           | 行・分岐カバレッジ。本体（単体+結合）で **LINE/BRANCH ≥ 85%**、`-Pe2e`（E2E 統合）で **LINE/BRANCH ≥ 95%**                                                                       | テストが書かれているかの「質」（= 何をアサートしているか）                                                                                                    | pre-push (report), CI (check)              |
| OWASP Dependency-Check                                           | `pom.xml` が引き込む **Java 依存** の既知 CVE（CVSS ≥ 7 で fail）                                                                                                                | JS / OS 依存 / コード自体                                                                                                                                     | CI                                         |
| Trivy fs                                                         | ファイルシステム / **JS 依存 (package-lock)** の HIGH/CRITICAL CVE                                                                                                               | Java 依存（OWASP DC が担当）                                                                                                                                  | CI                                         |
| Trivy image                                                      | 最終 Container Image の OS + 依存 CVE                                                                                                                                            | Dockerfile 記述スタイル                                                                                                                                       | CI                                         |
| Hadolint                                                         | Dockerfile の記述 anti-pattern                                                                                                                                                   | image の中身                                                                                                                                                  | CI                                         |
| Syft                                                             | CycloneDX 形式の SBOM 生成                                                                                                                                                       | 脆弱性検知そのもの（Trivy が担当）                                                                                                                            | CI                                         |
| Secretlint                                                       | 変更を含む全ファイルのシークレット文字列                                                                                                                                         | Git 履歴                                                                                                                                                      | pre-commit                                 |
| Gitleaks                                                         | Git 履歴全体のシークレット                                                                                                                                                       | 現在未 stage のファイル（secretlint が補完）                                                                                                                  | CI                                         |

---

## 4.1. なぜ Checkstyle は `checkstyle-google.xml` と `checkstyle-blocking.xml` の 2 ファイルに分かれているのか

`rules/checkstyle/` 配下に **役割の異なる 2 つの hard gate Checkstyle 設定** が共存しています。両方とも違反でビルド / コミットを落としますが、**カバー範囲と独自性が意図的に異なります**。

| 比較軸                    | `checkstyle-google.xml`                                                                                                                                              | `checkstyle-blocking.xml`                                                                                                                                                |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 中身                      | Google 公式の google_checks.xml 逐語コピー（350+ ルール）＋ Checker 直下 severity の 1 行だけ独自パッチ                                                              | プロジェクト独自の致命パターン集合（10 前後のルール）                                                                                                                    |
| Checker ルートの severity | `error`（独自パッチ）                                                                                                                                                | `error`                                                                                                                                                                  |
| 違反時の挙動              | Maven / CLI とも **exit 非ゼロ**（build / commit FAILURE）                                                                                                           | Maven / CLI とも **exit 非ゼロ**（build / commit FAILURE）                                                                                                               |
| 役割                      | **hard gate**（Google Java Style 全規則を網羅）                                                                                                                      | **hard gate**（Google の網羅では拾えない致命パターン）                                                                                                                   |
| 実行場所                  | **pre-commit**（`scripts/run-checkstyle.mjs --ruleset=google`、差分対象）＋ **pre-push / CI**（`maven-checkstyle-plugin` / `checkstyle-google` execution、全体対象） | **pre-commit**（`scripts/run-checkstyle.mjs --ruleset=blocking`、差分対象）＋ **pre-push / CI**（`maven-checkstyle-plugin` / `checkstyle-blocking` execution、全体対象） |
| 更新方針                  | Checker 直下 severity の 1 行のみが独自パッチ。それ以外は逐語コピー維持（checkstyle のバージョン更新時は最新版上書き → severity 1 行を再パッチ）                     | プロジェクト判断で随時追加。ただし誤検知が実質ゼロで修正方法が自明なものだけ                                                                                             |

### この設計の狙い

1. **"hard gate は一貫してブロックする"** を保証する：両 ruleset は pre-commit と pre-push/CI の双方で error として検査されるため、`git commit --no-verify` でバイパスされた変更や、差分以外に混入した違反も最終的に pre-push / CI で必ず捕捉される。pre-commit 限定の hard gate では shift-left の実効性が損なわれるため、二重に走らせている。
2. **Google Java Style 全規則を AI 主導開発の方針上 hard gate 化**：Jakarta EE ブランチでは Google Style からのあらゆる逸脱をコミット時点で気付かせるために google_checks.xml も hard gate に組み込む。Google 本家は同 ruleset を soft gate（severity=warning）の助言レベルで提供しているが、本ブランチでは Checker 直下 severity の 1 行のみを `error` に書き換えることで強制力を持たせる。逐語コピー原則を維持するため、それ以外の独自改変は **行わない**。
3. **役割分担を明確にする**：「Google が定める全規則 / プロジェクト固有の致命パターン」という観点の違いをファイル名レベルで分離することで、初見の開発者でも各ルールの取り扱いを一目で判断できるようにする。

### 運用上の帰結

- 新しいルールを **blocking に** 足したい場合：議論の余地がない致命パターンであることを必ず確認する（誤検知が実質ゼロで、修正方法が自明で、他のツール — Spotless / PMD / SpotBugs — でカバーされていないもの）。
- 新しいルールを **google に** 足したい場合：原則として足さない。google_checks.xml は逐語コピー＋severity 1 行パッチを維持する。Google Style そのものに対する例外を入れたい場合は §4.5 の判定フローを使い、まず `@SuppressWarnings("checkstyle:...")` での個別抑制を検討。複数ファイルで構造的に再発するなら、`google_checks.xml` 本体ではなく **別 ruleset ファイル**（例：`rules/checkstyle/checkstyle-project.xml`）として分離する設計変更を検討すること（逐語コピー原則を壊さないため）。
- pre-commit が重すぎると感じたら blocking ruleset の対象を絞る。pre-push / CI はどうせ全体を検査するため、pre-commit は "最速で止めたい致命" だけに集中させてよい。ただし Jakarta EE ブランチでは AI 主導の早期検知方針上、google も差分対象で走らせている。

---

## 4.5 ルールセット改定 vs 個別抑制 — 判定基準

新しい違反パターンに遭遇したときは以下のフローで対処する。「とりあえず `@SuppressWarnings` で消す」は禁止。サンプルアプリ実装時に `@SuppressWarnings` を散りばめてしまうと、ボイラープレート適用先のチームが「これが標準的な書き方なのか、抑制してよいのか」を判断できなくなるため。

```mermaid
flowchart TD
    A[静的解析違反が検出された] --> B{修正可能か?}
    B -- "Yes" --> C[コードを修正]
    B -- "No / 設計上不可避" --> D{同一パターンが<br/>複数ファイルで発生する<br/>構造的衝突か?}
    D -- "Yes" --> E[ルールセット改定<br/>rules/*.xml にて<br/>exclude / skip / property 緩和を追加]
    E --> F[rules/README.md §4.5 の<br/>「設定の現状と理由」に追記]
    D -- "No (単発の例外)" --> G[個別抑制<br/>@SuppressWarnings + 理由コメント必須]
    G --> H{この理由は他人に<br/>納得してもらえるか?}
    H -- "No" --> C
    H -- "Yes" --> I[コミット]
    F --> I
```

### ルールセット改定の手段（PMD 7 / SpotBugs 別）

- **PMD 7**：
  - ルール参照に `<exclude-pattern>` を **置けない**（PMD 7 の XSD 仕様変更）。ファイル単位で除外したい場合は `<ruleset>` 直下の `<exclude-pattern>` を使うか、ルール自体を `<exclude>` で除外する。
  - 同等効果を **rule property の緩和** で実現できる場合はそちらを優先（特定パッケージ／ファイル名に依存しない汎用設定にしやすい）。例：`MethodNamingConventions` の `junit5TestPattern`、`AvoidDuplicateLiterals` の `skipAnnotations`、`TooManyMethods` の `maxmethods`。
  - ルール自体を除外する判断は「フレームワークの構造と原理的に衝突する」ものに限定する（例：エントリポイントクラスが言語仕様上 `main` の `static` メソッドのみで構成される場合の `UseUtilityClass` 衝突）。
- **SpotBugs**：
  - `<Match>` 内で `<Class>` 正規表現と `<Bug pattern="...">` を組み合わせ、特定構造（パッケージ命名規約や特定アノテーション付きクラス）にだけ false positive を抑制する。
  - 命名は **ボイラープレート汎用** にする（特定サンプルアプリのパッケージ名を直書きしない）。例：JPA エンティティの `EI_EXPOSE_REP` 抑制は `~.*\.(domain|entity)\..*` の形で domain / entity 規約両対応にしておく。

### 設定の現状と理由

ボイラープレートが採用している主要な設定の現状と、その採用理由を示す。新たな構造的衝突に遭遇した場合は、§4.5 のフローで判定したうえでこのリストを更新する。

| 設定箇所                                                                                                                                    | 現在の設定値                                                                                                                                                                                                                                                      | 理由                                                                                                                                                                                                                                                                                                          |
| ------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `pom.xml` の `maven-pmd-plugin` ランタイム                                                                                                  | `${pmd.version}` (7.23.0) に固定                                                                                                                                                                                                                                  | プラグイン同梱の旧バージョンでは rule property override が効かない。pre-commit 経由の CLI とプラグインで PMD 実装バージョンを揃えて検出結果の乖離を防ぐ                                                                                                                                                       |
| `rules/checkstyle/checkstyle-google.xml`                                                                                                    | Google 公式 `google_checks.xml` の逐語コピー＋ Checker 直下 `severity="error"` の 1 行パッチ                                                                                                                                                                      | Google Java Style 全規則を hard gate 化する方針。逐語コピー原則を維持し、独自パッチは severity の 1 行のみに限定                                                                                                                                                                                              |
| `rules/checkstyle/eclipse-formatter.xml`                                                                                                    | Google 公式 `eclipse-java-google-style.xml` の逐語コピー                                                                                                                                                                                                          | 編集ノイズ混入を防ぐため逐語コピー原則                                                                                                                                                                                                                                                                        |
| `rules/pmd/ruleset.xml` の基本構成                                                                                                          | PMD 7.x 標準 8 カテゴリ参照を基本とし、構造的衝突のあるルールのみ exclude / property 緩和                                                                                                                                                                         | Google Java Style に厳密に沿う方針。構造的衝突が実際に発生した箇所のみ §4.5 のフローで個別判断                                                                                                                                                                                                                |
| `rules/checkstyle/suppressions.xml`, `rules/pmd/ruleset.xml`, `rules/spotbugs/exclude.xml`, `pom.xml` の `jacoco-maven-plugin` `<excludes>` | 社内ライブラリ準拠 IF パッケージ（`config` / `log.cdi` / `log.formatter` / `exception.**` / `userinfo.**`）を全 hard gate / カバレッジ計測から除外                                                                                                                | これらは内容変更不可（AGENTS.md）。個別 `@SuppressWarnings` では追従不能。汎用パッケージ名のみで指定するためボイラープレート再利用性を損なわない。独自実装側は除外対象外                                                                                                                                      |
| `rules/spotbugs/exclude.xml` の構造的 false positive 除外                                                                                   | `@SessionScoped` / `@ViewScoped` バッキングビーンの `SE_BAD_FIELD` 除外、Lombok `@Getter/@Setter` 経由参照露出の `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` 除外、構造的に発生しない `CRLF_INJECTION_LOGS` 除外                                                           | (a) コンテナがパッシベーション管理する CDI Proxy は Serializable を実装しない。(b) JSF バッキングビーンの本質パターン。(c) MessageId とパラメタ配列の形式に log 呼び出しを統一しているため CRLF 混入は構造的に発生しない。いずれもパッケージ／クラス命名規約に依存しない汎用形                                |
| `rules/pmd/ruleset.xml` の `TooFewBranchesForSwitch` 全体除外                                                                               | `category/java/performance.xml` の `<exclude>`                                                                                                                                                                                                                    | 将来拡張を想定した 少数値 enum の switch 式を if-else に書き換えると enum 拡張時の網羅性チェックが失われる。PMD CLI と maven-pmd-plugin で発火挙動が一致せず `@SuppressWarnings` が誤検知を引き起こす構造的問題                                                                                               |
| `pom.xml` の JaCoCo フラットカバレッジ閾値                                                                                                  | アプリ側パッケージ全体を `element=BUNDLE` 1 本で評価。本体: LINE/BRANCH 0.85、`-Pe2e`: LINE/BRANCH 0.95。Servlet Filter 等のコンテナ依存クラスは plugin-level `<excludes>` で個別除外（本体側のみ、`-Pe2e` では復活）。社内ライブラリ準拠 IF パッケージは常に除外 | 層別閾値は適用先で読み解くコストが大きいため採用しない。例外は Java クラス単位で `<excludes>` に追記し、根拠を ADR-004 の例外台帳で一元管理する（追加手順は ADR-004 参照）                                                                                                                                    |
| `pom.xml` の JaCoCo `<include>` パターン                                                                                                    | レイヤー直下にクラスを置くパッケージは `foo.bar` と `foo.bar.*` を **必ず両方併記** する（例: `<include>jp.mufg.it.rcb.adapter.in.web</include>` と `<include>jp.mufg.it.rcb.adapter.in.web.*</include>`）                                                        | JaCoCo の `<include>` は内部で正規表現化され全体一致で評価される（`foo.bar.*` → `foo\.bar\..*`）。末尾 `.*` のみではパッケージ自身にマッチせず、レイヤー直下のクラスがチェック対象から漏れて閾値が空振りする                                                                                                  |
| `rules/pmd/ruleset.xml` の構造的衝突吸収                                                                                                    | `CommentSize` の `maxLines=15` / `maxLineLength=120` 緩和、`OnlyOneReturn` の exclude、`ShortVariable.minimum=2`、`LongVariable.minimum=32`、テストクラス名末尾一致 (`*Test` / `*IT`) の XPath 抑制、`TooManyStaticImports.maximumStaticImports=20`               | いずれも「Spotless 自動整形との構造的衝突」「テストコードの記述慣行」「モダン Java の推奨パターン（Google Style 整合）」「業界標準命名」のどれかに該当し、ruleset で吸収しても本番品質を毀損しない。`@SuppressWarnings` 個別抑制が累積して読み手の判断負荷を上げる状態を回避するため ruleset 側で吸収する設計 |

### 方針整合：日本語コメント方針と Google Java Style 厳密準拠 hard gate の共存

AGENTS.md の「コードのコメントは日本語で記載する」方針と、`checkstyle-google.xml` の hard gate 化（Google Java Style 全規則の strict 準拠）が同居している。両者は基本的に両立するが、**Javadoc の細部仕様で構造的に衝突する可能性のあるルール** がいくつか存在する。衝突が顕在化した場合は以下を参考に対処する。

| 衝突候補ルール                                                               | 想定される衝突状況                                                                                                                                | 想定される対処                                                                                                                                                                                                                     |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SummaryJavadoc`（`period='.'` 既定）                                        | Javadoc 一行目の summary を日本語で書くと `。` で終わるが、google_checks は英文ピリオド `.` での終端を期待                                        | 1) Javadoc summary は英文ピリオド `.` で終わるよう開発規約を整える / 2) 個別箇所のみ `@SuppressWarnings("checkstyle:SummaryJavadocCheck")` / 3) 構造的に再発するなら別 ruleset ファイル（例：`checkstyle-project.xml`）で override |
| `MissingJavadocMethod`（`scope='public'` 既定）                              | すべての public メソッドに Javadoc が要求される。Lombok 生成 getter/setter や、自明な簡易メソッドでも Javadoc が必要になる                        | 1) Lombok を使う場合は `allowedAnnotations` に Lombok を追加検討 / 2) 個別 `@SuppressWarnings("checkstyle:MissingJavadocMethod")` / 3) 構造的に再発するなら別 ruleset ファイルで override                                          |
| `AbbreviationAsWordInName`（`allowedAbbreviationLength=0`）                  | `JpaConfig` / `MstException` / `RcbLogger` 等、業務略語 3 文字以上を許容しない命名が落ちる                                                        | 業務略語が必要な領域で限定除外、または個別 `@SuppressWarnings`                                                                                                                                                                     |
| `JavadocTagContinuationIndentation`、`SummaryJavadoc`（forbidden fragments） | 日本語 Javadoc では「@return the \*」「This method returns」を使わないため通常は衝突しないが、複数行 Javadoc のインデント仕様で衝突する可能性あり | 衝突発生時に個別判断                                                                                                                                                                                                               |

**判断基準**：

1. **個別 `@SuppressWarnings`** — 単発の例外（同パターンが再発しない見込み）の場合。理由をコメントで残すこと。
2. **ruleset の property 緩和** — 同パターンが複数ファイルで再発する場合。ただし google_checks.xml 本体は **触らない**（逐語コピー原則）。代わりに `checkstyle-google.xml` とは別の追加 ruleset ファイル（例：`rules/checkstyle/checkstyle-project.xml`）を pom.xml の 3 つ目の execution として配置し、そこで override を入れる設計変更を行う。
3. **コード規約側の調整** — 例：Javadoc summary は英文ピリオドで終わる、業務略語の使い方ガイドライン等。AGENTS.md と本 README にルールを記載する。

### カバレッジ閾値方針

JaCoCo の閾値チェックは、アプリ側パッケージ全体を `element=BUNDLE` 1 本でフラット評価する。クラス単位の例外は plugin-level `<excludes>` に列挙し、ADR-004 の例外台帳で根拠を一元管理する。

E2E カバレッジを採取できる構成では、check を **2 系統** で運用する:

- **本体（`-Pfast` / `-Pci-mr` / `-Pci-main`）**: 単体 + 結合テストで **LINE/BRANCH 85%** を hard gate
- **`-Pe2e`**: E2E カバレッジ (`jacoco-e2e.exec`) を merge した上で **LINE/BRANCH 95%** を hard gate（`combine.self="override"` で本体側 `<excludes>` のクラス単位例外を一部復活させて計測対象に戻す）

社内ライブラリ準拠 IF パッケージ（`config` / `log.cdi` / `log.formatter` / `exception.**` / `userinfo.**`）は内容変更不可・将来差し替え予定のため、独自実装と責任分離していずれの check 系統でも対象外。HTML レポートにも出力しない。

### クラス単位例外の追加手順

ボイラープレート適用先で、フラット閾値を達成できないクラスが発生した場合は次の手順で例外を追加する。

1. 構造側で改善できないか先に検討（テスト可能な POJO への分解、ヘルパ抽出、コンテナ依存の局所化など）
2. それでも達成不能であれば `pom.xml` の `jacoco-maven-plugin` `<configuration>/<excludes>` に classes path 形式（例: `jp/mufg/it/rcb/foo/Bar.class`）で追記
3. ADR-004 の例外台帳に「FQCN / 達成不能な理由 / 代替の品質担保策」を 1 行追記
4. `-Pe2e` でも対象外としたい場合は `-Pe2e` プロファイル側の `<excludes combine.self="override">` にも同じクラスを追記
5. `<excludes>` に入れたクラスは HTML レポートからも消える点を了承する

実装は `pom.xml` の `jacoco-maven-plugin` `<rules>`（本体: `check-coverage` / `-Pe2e`: `check-coverage-e2e` execution）と ADR-004 を参照。

---

## 5. Suppression（例外抑制）の方法

各ツールとも、**まずは個別のソース注釈で抑制**することを推奨します。ルールセット全体から外すのは最後の手段です。

| ツール     | 個別抑制                                                                                                                | 範囲抑制                                                                         |
| ---------- | ----------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Checkstyle | `@SuppressWarnings("checkstyle:<CheckName>")` または `// CHECKSTYLE.OFF: <CheckName>` … `// CHECKSTYLE.ON: <CheckName>` | `rules/checkstyle/suppressions.xml` に `<suppress files="..." checks="..."/>`    |
| PMD        | `@SuppressWarnings("PMD.<RuleName>")` または行末 `// NOPMD - 理由`                                                      | `rules/pmd/ruleset.xml` 内の `<rule ref=".../>` を展開し `<exclude name="..."/>` |
| SpotBugs   | `@SuppressFBWarnings(value = "...", justification = "...")`（com.github.spotbugs:spotbugs-annotations）                 | `rules/spotbugs/exclude.xml` に `<Match>...</Match>`                             |

抑制時は **理由をコメントまたは justification に必ず残す** こと。レビュアが後で追跡できるようにするためです。

---

## 6. ルール / バージョン更新手順

### 6.1 Checkstyle（google = google_checks / blocking）の更新

1. `pom.xml` の `<checkstyle.version>` を上げる。
2. google（google_checks）を更新する場合：同じタグの `google_checks.xml` を checkstyle 配布物（`checkstyle-all.jar` もしくは GitHub の同タグ）から取得し、`rules/checkstyle/checkstyle-google.xml` の **本家ヘッダ以降** を逐語で上書きする（独自プロパティ `org.checkstyle.google.suppressionfilter.config` の参照は維持される）。**上書き後に Checker 直下 `<property name="severity" value="warning"/>` を `<property name="severity" value="error"/>` に書き換える独自パッチを再適用する**（これがファイル本体への唯一の独自改変）。
3. blocking を更新する場合：`rules/checkstyle/checkstyle-blocking.xml` にルールを追加／削除する。ファイル冒頭の「収録方針」基準（誤検知が実質ゼロ・生産性を落とさない粒度）を満たすことを必ず確認。
4. `./mvnw checkstyle:check` を実行し、google・blocking の双方の挙動を棚卸し（どちらも hard gate なので exit コードで検査）。

### 6.2 PMD の更新

1. `pom.xml` の `<pmd.version>` を上げる。
2. `rules/pmd/ruleset.xml` はカテゴリ参照のみなので通常そのまま。ただし PMD のメジャー更新時はルール名の統合・改名に注意（例：PMD 7 で `EmptyFinallyBlock` は `EmptyControlStatement` に統合）。
3. `./mvnw pmd:check` を実行し、差分を棚卸し。

### 6.3 SpotBugs / FindSecBugs の更新

1. `pom.xml` の `<spotbugs.version>` / `<find-sec-bugs.version>` を上げる。
2. `./mvnw compile spotbugs:check` を実行し、差分を棚卸し。
3. 誤検知は `rules/spotbugs/exclude.xml` に追記（必ず `<!-- 理由 -->` を書く）。
4. FindSecBugs の検出パターンリスト（<https://find-sec-bugs.github.io/bugs.htm>）で新規 detector・改名を確認。

### 6.4 google-java-format（および eclipse-formatter.xml）の更新

1. `pom.xml` の `<google-java-format.version>` を上げる。
2. `google/styleguide` の同タグ相当から `eclipse-java-google-style.xml` を取得し、`rules/checkstyle/eclipse-formatter.xml` の XML 宣言以下を差し替える（VSCode の redhat.java と整合を取るため）。
3. `./mvnw spotless:apply` でリポジトリ全体を再整形し、差分をコミット。

---

## 7. 運用上の補足

- **`--no-verify` は原則禁止**。やむを得ずバイパスした場合はコミットメッセージに理由を記載し、CI で等価以上のチェックが走ることを前提とする（詳細はルート README § `--no-verify` の扱い）。
- **pre-commit で止まったとき**：まず出力に従い `./mvnw spotless:apply` や `@SuppressWarnings` 追加で対処する。`run-*.mjs` が jar を見つけられない旨のエラーが出たら `node scripts/setup-lint-tools.mjs` を再実行。
- **pre-push が遅いと感じたとき**：`./mvnw -T 1C -Pfast verify` をローカルで先行実行しておくと push 時には Maven のインクリメンタル差分のみで済む。
- **`.lint-tools/` の中身**：`npm install` の postinstall で自動配置される Checkstyle / PMD / google-java-format の jar 群。プロジェクトルート直下（`.gitignore` 対象）に置くことで `mvn clean` の影響を受けない。手動で消した場合は `node scripts/setup-lint-tools.mjs`。

---

## 関連ドキュメント

- ルート全体像・開発サイクル・社内ネットワーク対応：[`../README.md`](../README.md)
- プロジェクト制約・技術構成：[`../AGENTS.md`](../AGENTS.md)
- PBI（プロジェクトバックログ）：[`../PBI.md`](../PBI.md)
