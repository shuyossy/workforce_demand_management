# トラブルシューティング

## WildFly が起動しない

起動方式（Docker Compose / ローカル WildFly）によって調査対象が異なる。

### Docker Compose モード（`WILDFLY_HOME` 未設定）

- `docker compose -f docker/dev/docker-compose.yml logs wildfly` でコンテナログ確認（F5 起動中は `wildfly:tail-log` ターミナルにも follow 表示）
- `docker ps` でコンテナが起動しているか確認
- ポート 8080 / 9990 / 8787 / 5432 が他プロセスに使われていないか確認

### ローカル WildFly モード（`WILDFLY_HOME` 設定）

- `.dev-runtime/wildfly.log` を参照（`scripts/wildfly-ensure-running.sh` が nohup で出力。F5 起動中は `wildfly:tail-log` ターミナルにも follow 表示）
- `.dev-runtime/wildfly.pid` を見て、`ps -p <PID>` でプロセスが生きているか
- `$WILDFLY_HOME/bin/standalone.sh` が実行可能か（`chmod +x`）
- `$WILDFLY_HOME/standalone/configuration/mgmt-users.properties` に `admin=` 行があるか（無ければ `scripts/wildfly-local-setup.sh` を直接実行）
- `$WILDFLY_HOME/modules/system/layers/base/org/postgresql/main/postgresql.jar` が存在するか
- ポート 8080 / 9990 / 8787 が他プロセスに使われていないか（Docker Compose 残骸を含む）

## Flyway が DB 接続できない

- `DB_URL` / `DB_USER` / `DB_PASSWORD` が `.env` で正しく設定されているか
- `psql` で手動接続可能か
- PostgreSQL の `pg_hba.conf` で `localhost` からの接続が許可されているか

## PrimeFaces 画面の CSS が崩れる

- `target/rcb-0.0.1-SNAPSHOT.war` を再ビルドしてデプロイ
- ブラウザのキャッシュをクリア（Cmd+Shift+R）

## 外部マシンからアクセスできない

- 両モードとも `-b 0.0.0.0 -bmanagement 0.0.0.0` で起動するため、開発機 OS のファイアウォール設定を確認
- リモートマシンからは `http://<開発機 IP>:8080/rcb/tasks/list.xhtml` でアクセス
