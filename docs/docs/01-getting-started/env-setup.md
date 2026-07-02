# 環境変数の設定

`.env.example` をテンプレートとして `.env` を作成する。

## 手順

1. `cp .env.example .env`
2. 各変数を環境に合わせて編集
3. WildFly の起動モードを選択：
   - **Docker Compose モード（既定）**: `WILDFLY_HOME` を未設定/コメントアウトのままにする
   - **ローカル WildFly モード**: `WILDFLY_HOME=/path/to/wildfly-32.0.1.Final` を指定

## DB の事前作成（一度だけ）

Docker Compose モードを利用する場合はコンテナが自動作成するため不要。ローカル PostgreSQL を利用する場合：

```bash
createdb rcb
createuser rcb -P  # パスワードを設定
```

## 起動モード比較

| モード                                  | 起動方法                                                                                                | DB                                     | 初期セットアップ                                                                                                                   |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Docker Compose（`WILDFLY_HOME` 未設定） | `docker compose -f docker/dev/docker-compose.yml up -d --build`                                         | コンテナ (postgres:16-alpine)          | `docker/dev/wildfly/Dockerfile` がイメージビルド時に admin/admin と PostgreSQL JDBC モジュールを焼き込み                           |
| ローカル WildFly（`WILDFLY_HOME` 設定） | `nohup $WILDFLY_HOME/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 --debug *:${WILDFLY_DEBUG_PORT}` | ローカル PostgreSQL（`DB_URL` で指定） | 初回起動時に `scripts/wildfly-local-setup.sh` が admin/admin と PostgreSQL JDBC モジュールを冪等に整備。PID/ログは `.dev-runtime/` |

どちらのモードでも `-b 0.0.0.0 -bmanagement 0.0.0.0` で起動するため、社内ネットワーク内の別マシンからも `http://<開発機 IP>:8080/rcb/` でアクセス可能。

## 変数の役割

主要な変数：

| 変数                    | 用途                             | デフォルト                             |
| ----------------------- | -------------------------------- | -------------------------------------- |
| `DB_URL`                | JDBC URL                         | `jdbc:postgresql://localhost:5432/rcb` |
| `DB_USER`               | DB ユーザ                        | `rcb`                                  |
| `DB_PASSWORD`           | DB パスワード                    | （無し、必須）                         |
| `WILDFLY_HOME`          | WildFly インストールパス（任意） | 未設定 → Docker Compose で起動         |
| `WILDFLY_HOST`          | 管理接続ホスト                   | `localhost`                            |
| `WILDFLY_MGMT_PORT`     | management port                  | `9990`                                 |
| `WILDFLY_DEBUG_PORT`    | JVM debug port                   | `8787`                                 |
| `WILDFLY_USER`          | 管理ユーザ名                     | `admin`                                |
| `WILDFLY_PASSWORD`      | 管理ユーザパスワード             | `admin`                                |
| `APP_CONTEXT_ROOT`      | コンテキストルート               | `/rcb`                                 |
| `APP_AUTH_MODE`         | `DEV_LOGIN` / `HEADER`           | `DEV_LOGIN`                            |
| `APP_EXTERNAL_BASE_URL` | 外部URL 絶対指定（メール等）     | 空                                     |
| `BASE_URL`              | E2E テストの基底 URL             | `http://localhost:8080/rcb`            |

`.env.example` をコミット、`.env` は `.gitignore`。

## WildFly 制御フロー（開発 / E2E / 本番）

WildFly はシナリオごとに「誰が起動するか」「いつ CLI を適用するか」「設定がどこに永続化されるか」が異なる。下表で全体像を把握できる。

| シナリオ                   | 起動方法                                                                                                                                                 | DB                                                        | CLI 適用タイミング                                                                                  | 設定の永続化                                                                                                                         |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 開発: Docker Compose       | `docker compose -f docker/dev/docker-compose.yml up -d --build`（F5 から自動）                                                                           | コンテナの PostgreSQL（compose 内部ネットワーク）         | F5 の `wildfly:apply-config` が起動後に management port (9990) 経由で `01-04*.cli` を投入           | コンテナ内 `standalone.xml`。`docker compose down -v` でリセット                                                                     |
| 開発: ローカル WildFly     | `$WILDFLY_HOME/bin/standalone.sh`（F5 → `scripts/wildfly-ensure-running.sh`）                                                                            | `.env` の `DB_URL` が指す PostgreSQL（ローカル/リモート） | 同上                                                                                                | `$WILDFLY_HOME/standalone/configuration/standalone.xml` に書き込まれる                                                               |
| E2E: `./mvnw -Pe2e verify` | `scripts/wildfly-ensure-running.sh`（package phase, `E2E_MODE=1`。既存 WildFly があれば auto-stop 後にフレッシュ起動して JaCoCo agent を JVM に attach） | H2 file (`target/h2/app-e2e*`、`MODE=PostgreSQL`)         | `pre-integration-test` で `01-datasource-h2.cli` + `02-04*.cli`（`combine.self="override"` で差替） | standalone.xml に永続化（次回デバッグ時は `01-datasource-postgres.cli` が自動で復旧）。`post-integration-test` で WildFly を必ず停止 |
| 本番: Docker イメージ      | ルートの `Dockerfile`（multi-stage build → `quay.io/wildfly/wildfly:32.0.1.Final`）                                                                      | 環境変数 `DB_URL` で指定する PostgreSQL（運用環境）       | build 時に offline CLI で焼き込み（イメージ内 `standalone.xml` 完成済）                             | コンテナイメージ内。起動後は読み取り専用扱い                                                                                         |

### E2E ↔ 通常デバッグの自動切替

CLI で登録した datasource は `standalone.xml` に永続化されるため、E2E 後にローカル WildFly を再利用すると H2DS / h2-e2e driver が残ったままになる。WildFly の datasource subsystem は `data-source remove` 時に `process-state=reload-required` を伴うため、online CLI 単独では同 JNDI への切替や jdbc-driver の remove ができない（DuplicateServiceException / WFLYCTL0171）。

このため、`scripts/wildfly-ensure-running.sh` が WildFly 起動前に `wildfly/cli/00-offline-cleanup.cli` を **embed-server モード**（standalone.xml を直接書き換える offline モード）で実行し、`PostgresDS` / `H2DS` / `postgresql` / `h2-e2e` を一括して取り除く。起動後の `01-datasource-*.cli` がクリーンな状態から冪等に再登録するため、利用者は何も操作せず E2E ↔ デバッグの往復ができる。

E2E モード (`E2E_MODE=1`) では、JaCoCo agent を JVM 起動時に attach する必要があるため、F5 デバッグで起動済みの WildFly はそのままでは流用できない。`wildfly-ensure-running.sh` は pre-existing WildFly を検出すると自動的に `scripts/wildfly-stop.sh` を呼んでから上記 offline cleanup → fresh start のフローに合流する。E2E 終了後も `post-integration-test` フェーズで必ず stop する（escape hatch なし）。詳細は [初回起動](./first-run) の「E2E ↔ 通常デバッグの切替」節を参照。

## WildFly 制御ファイル早見表

WildFly の挙動に関わる設定は複数の場所に分散している。トラブル時の起点把握用の早見表:

| ファイル                                 | 役割                                                                                                                                                                                  |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `wildfly/cli/00-offline-cleanup.cli`     | 起動前 offline クリーンアップ。embed-server モードで前回セッションの PostgresDS / H2DS / postgresql / h2-e2e を一括削除し、reload-required 制約を回避                                 |
| `wildfly/cli/01-datasource-postgres.cli` | main プロファイル用 PostgresDS 登録（クリーンな状態から冪等 add）                                                                                                                     |
| `wildfly/cli/01-datasource-h2.cli`       | e2e プロファイル用 H2DS を `java:/PostgresDS` で登録（persistence.xml を変更しないため同 JNDI 名を再利用）                                                                            |
| `wildfly/cli/02-system-properties.cli`   | `APP_CONTEXT_ROOT` を WildFly system property に転写（`jboss-web.xml` が参照）                                                                                                        |
| `wildfly/cli/03-logging.cli`             | `jp.mufg.it.rcb` ロガーレベル（INFO）と CONSOLE フォーマッタの MDC pattern (`requestId` / `empNum`)                                                                                   |
| `wildfly/cli/04-proxy-forwarding.cli`    | 認証サーバ（リバプロ）の前置きに対応する `proxy-address-forwarding=true`                                                                                                              |
| `scripts/wildfly-ensure-running.sh`      | 起動エントリポイント。`WILDFLY_HOME` の有無で Docker Compose / ローカルを切替、ローカル起動時は `00-offline-cleanup.cli` を実行してから start、E2E 時は JaCoCo agent を JVM に attach |
| `scripts/wildfly-local-setup.sh`         | ローカル WildFly の冪等初期化（管理ユーザ + PostgreSQL / H2 JDBC モジュール配置）                                                                                                     |
| `scripts/wildfly-stop.sh`                | management API 経由の graceful shutdown                                                                                                                                               |
| `pom.xml` `wildfly-maven-plugin`         | `execute-commands` で CLI を management port 経由で投げる Maven 統合（接続情報は `.env` 経由）                                                                                        |
| `.vscode/tasks.json` `wildfly:*`         | 上記 Maven plugin と scripts のラッパー（`bash scripts/with-env.sh` で `.env` ロード）                                                                                                |
| `.vscode/launch.json`                    | F5 で `dev:bootstrap-and-deploy` を preLaunchTask として実行し、JDWP port 8787 に attach                                                                                              |
| `docker/dev/wildfly/Dockerfile`          | 開発用 WildFly イメージ build（admin/admin 焼き込み + PostgreSQL / H2 JDBC モジュール配置）                                                                                           |
| `Dockerfile`（リポジトリルート）         | 本番用 multi-stage build（Maven build → WildFly イメージ）                                                                                                                            |

## セキュリティ警告

本ボイラープレートは**社内プライベートネットワーク内のローカル開発**前提。以下は本番では必ず変更すること：

- `-b 0.0.0.0` / `-bmanagement 0.0.0.0`：すべての NIC で listen するため、本番では management を内部 IF に限定するか firewall で隔離
- `admin/admin` の弱パスワード：`WILDFLY_USER` / `WILDFLY_PASSWORD` を本番用の強パスワードに置換
- `WILDFLY_DEBUG_PORT` を有効化したまま本番デプロイしない（リモートデバッグ経路が開く）
