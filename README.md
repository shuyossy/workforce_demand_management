# rcb (jakartaEE branch)

Jakarta EE 系列の AI 主導開発に対応したモダンな開発環境ボイラープレート。WildFly 32.0.1.Final + PostgreSQL + Flyway + PrimeFaces による認証不要のタスク管理サンプルアプリ（一覧・作成・完了）を内蔵し、適用先プロジェクトはサンプル層を差し替えるだけで、品質ゲート・CI/CD・ドキュメントサイト・Docker ビルドをそのまま利用できる。

シフトレフト（セキュリティを含むソフトウェア品質の担保）を大前提として、ローカルでの pre-commit / pre-push フックと GitLab CI/CD パイプラインで、バグや脆弱性をリモートに持ち込まない・デプロイさせない仕組みを提供する。AI 主導開発の方針上、Google Java Style 全規則と PMD 全カテゴリの両方を **コミット時点から hard gate** として強制する。

## サンプルアプリ（適用先で削除）

`src/` 配下に Jakarta EE 10 / WildFly 上で動作する認証不要のタスク管理サンプルが含まれる。タスクのタイトルを入力して作成し、一覧から完了操作を行うだけの最小構成で、ログイン画面やセッション認証は持たない。これは使い方を示すための同梱物であり、適用先プロジェクトでは [サンプル削除ガイド](docs/docs/01-getting-started/strip-sample.md) に従って撤去してから自案件のドメインで開発を始める。

- アーキテクチャ：クリーン/ヘキサゴナル（`domain` / `application` / `adapter.in.web` / `adapter.out.persistence`）、ArchUnit で層境界を hard gate
- 画面：PrimeFaces（xhtml ファイルに JS を個別バインド、バンドル不使用）
- 永続化：PostgreSQL + Flyway マイグレーション、テスト時は H2 インメモリ
- ロギング：社内ライブラリ準拠 IF の CDI Logger（`jp.mufg.it.rcb.log.cdi`）+ `messages.properties` ベースの ID 付きメッセージ
- 認証：本サンプルは認証機能を持たない（将来、社内認証サーバ越し（junction-path 規約）の運用を前提とした URL ビルダは同梱済みで、認証導入時にそのまま活用できる）
- テスト：JUnit + AssertJ + Mockito（単体・結合） / Playwright（E2E） / k6（性能）

サンプル削除後は `src/main/java/jp/mufg/it/rcb/{shared, log, exception, userinfo, config}` および ArchUnit / 共通テスト基盤・品質ゲート・CI/CD・ドキュメント枠組みを再利用できる（→ [サンプル削除ガイド](docs/docs/01-getting-started/strip-sample.md) の §残るスモークテスト 参照）。

## 前提環境

| ツール     | バージョン   | 備考                                                                                     |
| ---------- | ------------ | ---------------------------------------------------------------------------------------- |
| JDK (実行) | 17 (LTS)     | Maven ビルド・WildFly 実行用。`maven.compiler.release=17`                                |
| JDK (IDE)  | 21 (LTS)     | VSCode Extension Pack for Java の動作要件。macOS なら `/usr/libexec/java_home -V` で確認 |
| Node.js    | 22 (LTS)     | `.nvmrc` で固定。`nvm use` で切替                                                        |
| Docker     | 27 以降      | Docker Desktop または Docker Engine。BuildKit 対応が必要                                 |
| WildFly    | 32.0.1.Final | ローカル開発用。本番は Docker イメージで起動                                             |
| PostgreSQL | 16 以降      | ローカル開発用。datasource 名は `PostgresDS`                                             |

### VSCode 拡張機能（推奨、動作前提）

`.vscode/settings.json` / `.vscode/tasks.json` / `.vscode/launch.json` は以下を前提に設計：

- Extension Pack for Java (`vscjava.vscode-java-pack`)
- ESLint (`dbaeumer.vscode-eslint`)
- Prettier (`esbenp.prettier-vscode`)
- EditorConfig (`editorconfig.editorconfig`)
- Checkstyle for Java (`shengchen.vscode-checkstyle`)
- GitLab Workflow (`gitlab.gitlab-workflow`)

## 初回セットアップ

```bash
nvm use                             # .nvmrc に従って Node 22 へ切替
npm install                         # 依存導入 + husky 有効化 + lint-tools 自動展開
./mvnw -N dependency:go-offline     # Maven 依存のウォームアップ
```

`npm install` の `postinstall` で `scripts/setup-lint-tools.mjs` が走り、Checkstyle / PMD / google-java-format の jar が `.lint-tools/`（プロジェクトルート直下、`.gitignore` 対象）に自動配置されます。これにより開発者は追加のバイナリインストール不要。配置先を `target/` 外にしているため、`./mvnw clean ...` を実行しても jar 群は失われず、pre-commit / pre-push が再 setup なしで動作します。

### 動作確認

```bash
./mvnw clean verify                 # 5 段の静的解析 + 単体/結合テスト + JaCoCo 層別閾値検証（BUILD SUCCESS を確認）
./mvnw clean package -DskipTests    # WAR 生成
cd docs && npm run build            # docusaurus サイトのビルド
```

## 開発サイクル

### コミットまで（pre-commit / commit-msg）

`git commit` 時に以下が自動実行されます（所要 〜30 秒）：

- **pre-commit**：
  - lint-staged が変更ファイル毎に：
    - `*.java` → Spotless (google-java-format) → Checkstyle blocking → **Checkstyle google（全規則 hard gate）** → PMD blocking → **PMD full（全カテゴリ hard gate）** の 5 段
    - `*.{js,mjs,cjs}` → Prettier → ESLint (`--max-warnings=0`)
    - `*.{json,md,yml,yaml}` → Prettier
    - 全ファイル → Secretlint でシークレット検知
- **commit-msg**：commitlint で Conventional Commits 規約を検証
  - 許可 type: `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `revert`

差分ファイル単位の 5 段 hard gate の設計理由と運用方針は `rules/README.md` を参照。

### プッシュまで（pre-push）

`git push` 時に以下が自動実行されます（所要 1〜3 分）：

- ESLint 全体
- Prettier `format:check`
- `npm audit` (`--audit-level=high`)
- `./mvnw -Pfast verify`：Checkstyle google + Checkstyle blocking + PMD full + SpotBugs+find-sec-bugs + JUnit + ArchUnit + JaCoCo 層別カバレッジ検証

差分単位化が困難な SpotBugs / JUnit / ArchUnit / JaCoCo は pre-push と CI で全体実行する責務分担になっています。

### `--no-verify` の扱い

ローカルフックは `git commit --no-verify` / `git push --no-verify` でバイパス可能ですが、**原則禁止**。例外的にバイパスした場合は、コミットメッセージ本文に理由を記載してください。CI 側で同等以上のチェックが必ず走るため、最終的な品質ゲートは GitLab のブランチ保護（保護ブランチへの直接 push 禁止 + MR パイプライン成功を merge 条件化）で担保します。

## CI/CD パイプライン

`.gitlab-ci.yml` + `.gitlab/ci/*.yml` が以下 2 系統のパイプラインを定義します：

| トリガ                | Stage 構成                                  | 概要                                                                                                  |
| --------------------- | ------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| **MR パイプライン**   | setup → quality → security → package → scan | Merge Request 作成・更新時。保護ブランチへのマージ可否判定。Docker image は build まで（push しない） |
| **main パイプライン** | MR と同じ + pages + release                 | 保護ブランチ push 時。Container Registry に image push、GitLab Pages に docusaurus デプロイ           |

主要ジョブ：

- **quality stage**：Prettier / ESLint / Checkstyle (google + blocking) / PMD / SpotBugs+find-sec-bugs / JUnit + ArchUnit / JaCoCo / Gitleaks
- **security stage**：OWASP Dependency-Check (Java SCA) / Trivy fs (JS SCA)
- **package stage**：Maven `package` で WAR 生成 → マルチステージ Docker ビルドで WildFly イメージ作成
- **scan stage**：hadolint / Trivy image（CVE HIGH 以上で fail）
- **pages stage**：docusaurus → GitLab Pages デプロイ（保護ブランチ限定）
- **release stage**：Container Registry に image push（保護ブランチ限定、`CI_REGISTRY` 設定時のみ）

## 社内プライベートネットワーク環境への適用

本ボイラープレートは社内プライベートネットワークから Maven Central および npm registry にアクセス可能な環境を前提としていますが、それ以外の外部インターネット接続が必要な箇所は以下 4 点です。社内ミラー / プロキシを設定してください：

| #   | 設定箇所                                                                             | 切替対象                                           | 既定値                               |
| --- | ------------------------------------------------------------------------------------ | -------------------------------------------------- | ------------------------------------ |
| 1   | `.gitlab/ci/_defaults.yml` の `IMAGE_*` 変数                                         | Docker Hub の各種公式イメージ                      | `maven:3.9-eclipse-temurin-21` 等    |
| 2   | `.gitlab/ci/_defaults.yml` の `TRIVY_DB_REPOSITORY` / `TRIVY_JAVA_DB_REPOSITORY`     | Trivy 脆弱性 DB（`ghcr.io/aquasecurity/trivy-db`） | コメントアウト済（既定は ghcr）      |
| 3   | `pom.xml` の `ci-mr` profile 内 OWASP Dependency-Check 設定 (`<nvdApiServerUrl>` 等) | NIST NVD への直接アクセス                          | コメントアウト済（既定は NIST 直接） |
| 4   | `pom.xml` の `<node.download.root>` / `<npm.download.root>` プロパティ               | `nodejs.org/dist/` からの Node バイナリ取得        | `https://nodejs.org/dist/`           |

## Docker

```bash
# マルチステージビルド（Maven で WAR 生成 → WildFly イメージへ deploy）
DOCKER_BUILDKIT=1 docker build -t rcb:local .

# 起動（PostgreSQL は別途用意。WildFly が 8080/9990 を公開）
# 環境変数 DB_NAME / DB_USER / DB_PASSWORD は適用先プロジェクトの DB 設定に合わせて置換。
docker run --rm -p 8080:8080 -p 9990:9990 \
  -e DB_HOST=host.docker.internal -e DB_PORT=5432 \
  -e DB_NAME=app -e DB_USER=app -e DB_PASSWORD=app \
  rcb:local

# Trivy スキャン（ローカル検証用）
docker run --rm aquasec/trivy:latest image rcb:local
```

`wildfly/cli/` 配下の CLI スクリプト（プロジェクトルート直下）でデータソース（`PostgresDS`）/ system properties / logging / proxy forwarding を起動前に適用します。接続情報は環境変数で渡します（CLI 内で `if outcome != success` 構文を使って冪等化済み）。

## ドキュメント

`docs/` 配下は docusaurus プロジェクトです（ルートの `package.json` とは独立）。

```bash
cd docs
npm install
npm start                # http://localhost:3000 で起動
npm run build            # 本番ビルド → docs/build/
```

サイト構成：

- `01-getting-started/` — 前提環境 / 環境変数 / 初回起動
- `02-requirements/` — 要件定義
- `03-adrs/` — Architecture Decision Records
- `04-domain-design/` — ドメイン設計
- `05-technical-design.md` — フォルダ構成 / ロギング / エラーハンドリング / データアクセス
- `06-operations/` — CI/CD / Docker / Flyway
- `07-quality/` — 静的解析 / 品質レポート

本番公開は GitLab Pages（保護ブランチへの push 時に自動デプロイ）。

## ディレクトリ構造

```
.
├── .gitlab/ci/              CI stage 定義（ジョブ分割）
├── .husky/                  Git フック（pre-commit / commit-msg / pre-push）
├── .vscode/                 共有 VSCode 設定（settings / tasks / launch）
├── docs/                    docusaurus（要件 / ADR / 技術設計 / 運用 / 品質）
├── rules/                   Checkstyle / PMD / SpotBugs ruleset と品質担保戦略ドキュメント
├── scripts/                 lint-staged 用ラッパー（Java tool 起動を Node から隠蔽）
├── src/main/java/jp/mufg/it/rcb/
│   ├── domain/, application/, adapter/  サンプルアプリ本体（認証不要のタスク管理、適用先で削除→ docs/01-getting-started/strip-sample.md）
│   ├── shared/              ボイラープレート共通基盤（config / web / message）
│   └── config/log/exception/userinfo/  社内ライブラリ準拠 IF（変更禁止、社内ライブラリ置換時に差し替え）
├── src/main/resources/db/migration/  Flyway マイグレーション
├── wildfly/cli/             WildFly CLI スクリプト（datasource / logging 等、運用設定）
├── Dockerfile               マルチステージ Docker ビルド（Maven → WildFly）
├── pom.xml                  Maven（Jakarta EE 10 / 品質プラグイン群 / profiles）
├── package.json             ルート開発ツール（husky / lint-staged / commitlint 等）
└── <設定ファイル群>         eslint.config.js / .prettierrc.json / commitlint.config.js / .secretlintrc.json / .editorconfig / .nvmrc / .gitignore / .gitattributes
```

## ライセンス

未定。
