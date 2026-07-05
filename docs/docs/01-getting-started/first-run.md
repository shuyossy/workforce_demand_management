# 初回起動

## VSCode から F5

1. `.env` を作成済みであること
2. VSCode で本プロジェクトを開く
3. F5 を押下 → `dev:bootstrap-and-deploy` が自動実行され、WildFly 起動 + Flyway + CLI 適用 + WAR デプロイ + デバッガアタッチが順次走る
4. ブラウザで `http://localhost:8080/rcb/tasks/list.xhtml` を開く（本サンプルは認証機能を持たないため、ログイン操作なしでタスク一覧が表示される）

## F5 で起こること

`.vscode/launch.json` の preLaunchTask `dev:bootstrap-and-tail` は `dev:bootstrap-and-deploy`（4 ステップ）と `wildfly:tail-log`（dedicated ターミナルでアプリログ tail）を **並列実行** する compound タスク。`dev:bootstrap-and-deploy` 側は `dependsOrder: sequence` で 4 ステップに展開される:

1. **`wildfly:ensure-running`** — `scripts/wildfly-ensure-running.sh`
   - management port (9990) が応答するなら no-op、未起動なら起動。
   - `.env` の `WILDFLY_HOME` が未設定なら Docker Compose、設定済みならローカル `standalone.sh` でブート。
   - ローカルブート時は start 前に `wildfly/cli/00-offline-cleanup.cli` を embed-server モードで実行し、前回セッションの datasource 残骸を取り除く（後述「E2E ↔ 通常デバッグの切替」参照）。
2. **`flyway:migrate`** — Maven `flyway-maven-plugin`
   - `src/main/resources/db/migration/V*.sql` を `.env` の DB に適用。
3. **`wildfly:apply-config`** — Maven `wildfly:execute-commands`
   - `pom.xml` の `<scripts>` で指定された `wildfly/cli/01-datasource-postgres.cli` 〜 `04-proxy-forwarding.cli` を順に投入。すべて冪等で再実行可。
4. **`wildfly:redeploy`** — Maven `package wildfly:deploy -Dwildfly.force=true`
   - WAR を最新ビルドしてデプロイ。既存デプロイは置き換わる。

その後 VSCode が JDWP port 8787 にデバッガを attach する（ブレークポイントが効くようになる）。

## デバッグ中のアプリログの見方

attach 型デバッグ（`launch.json` の `"request": "attach"`）は JDWP プロトコル仕様上 debuggee の stdout を Debug Console に流せない。このため `dev:bootstrap-and-tail` が並列起動する `wildfly:tail-log` 専用ターミナル（Terminal panel に `wildfly-logs` グループで表示）に、`scripts/wildfly-tail-log.sh` がモード別にログを follow する:

- ローカル WildFly モード（`WILDFLY_HOME` 設定）: `tail -F .dev-runtime/wildfly.log`（`wildfly-ensure-running.sh` が nohup で出している stdout/stderr）
- Docker Compose モード（`WILDFLY_HOME` 未設定）: `docker compose -f docker/dev/docker-compose.yml logs -f wildfly`

`@InjectLogger` 経由のアプリログ（`RcbLogFormatter` 整形済み）も WildFly default の ConsoleHandler 経由でこの tail に出力される。Debug Console には JDWP プロトコル情報のみ表示される（仕様）。

## E2E ↔ 通常デバッグの切替

`./mvnw -Pe2e verify` は **既存 WildFly があれば必ず停止してからフレッシュ起動** する。E2E は JaCoCo agent を JVM 起動時に attach する必要があり、F5 デバッグ起動の WildFly（agent 非アタッチ・PostgresDS 構成）はそのまま流用できないため、`scripts/wildfly-ensure-running.sh` が pre-existing を検出すると自動的に `scripts/wildfly-stop.sh` を呼び、management port が空くのを待ってから後段の起動フローへ合流する。E2E 終了後の WildFly stop も `post-integration-test` フェーズで必ず実行される（escape hatch なし）。

WildFly CLI による datasource 設定は `standalone.xml` に永続化されるため、E2E 終了後の `standalone.xml` には `H2DS` / `h2-e2e` driver が残る。WildFly の datasource subsystem は `data-source remove` 時に `process-state=reload-required` を伴うため、online CLI 単独で同 JNDI への切替や jdbc-driver の remove ができない（`DuplicateServiceException` / `WFLYCTL0171`）。このため `scripts/wildfly-ensure-running.sh` は WildFly を normally に start する前に `wildfly/cli/00-offline-cleanup.cli` を **embed-server モード** （`standalone.xml` を直接書き換える offline モード）で実行し、`PostgresDS` / `H2DS` / `postgresql` / `h2-e2e` を一括して取り除く。

これにより以下のいずれの方向でも **利用者は何も操作せず datasource 構成が切り替わる**：

- **E2E → F5**：E2E 後に F5 を押すと、`wildfly:ensure-running` がまず offline cleanup で datasource を全消し → WildFly start → `wildfly:apply-config` が `01-datasource-postgres.cli` でクリーンな PostgresDS / postgresql driver を登録。
- **F5 → E2E**：F5 デバッグ稼働中に `./mvnw -Pe2e verify` を実行すると、まず既存 WildFly を auto-stop → offline cleanup → JaCoCo agent 同梱で再起動 → `01-datasource-h2.cli` で H2DS を登録。

なお `target/h2/app-e2e*` の H2 ファイルは `mvn clean` で削除される一時 DB（永続化目的ではない）。

## 開発フロー

- コード変更 → F5（再 attach）または `wildfly:redeploy` タスクで反映
- DB をクリーンにしたい：`flyway:reset` タスク（確認ダイアログで `yes` 必須）
  - `flyway:clean` → `flyway:migrate` → `sql:execute@seed-dev`（dev-seed プロファイル）を順に実行

## 参考

- WildFly 制御ファイルの一覧と各シナリオの全体像は [環境変数の設定 §WildFly 制御フロー](./env-setup#wildfly-制御フロー開発--e2e--本番) を参照
