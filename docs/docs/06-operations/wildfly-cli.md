# WildFly CLI

`wildfly/cli/` 配下の CLI 設定ファイルを `wildfly-maven-plugin` の `execute-commands` ゴールで適用する。**すべて冪等**であること（複数回適用しても破綻しない）。

> ボイラープレートでは CLI スクリプトをプロジェクトルート直下の `wildfly/cli/` に配置している（Maven の `src/main/` 配下は WAR に組み込む素材のみ、CLI は運用設定のため `src/` 外に分離）。

## CLI 一覧

| ファイル                     | 役割                                         |
| ---------------------------- | -------------------------------------------- |
| `01-datasource-postgres.cli` | `PostgresDS` の作成、JNDI `java:/PostgresDS` |
| `02-system-properties.cli`   | アプリ向け system property                   |
| `03-logging.cli`             | logging.properties 連携                      |
| `04-proxy-forwarding.cli`    | Undertow `proxy-address-forwarding=true`     |

## 冪等性確保パターン

すべての `add` 操作は `if (outcome != success) of ...:read-resource` ガードで囲む：

```
if (outcome != success) of /subsystem=datasources/data-source=PostgresDS:read-resource
  data-source add \
    --name=PostgresDS \
    --jndi-name=java:/PostgresDS \
    --driver-name=postgresql \
    --connection-url=${env.DB_URL} \
    --user-name=${env.DB_USER} \
    --password=${env.DB_PASSWORD}
end-if
```

これにより、既に存在するリソースを再追加してエラーになることを防ぐ。

## WildFly 32 系の特記事項

- offline モード対応：embedded サーバへの CLI 適用も可能だが、本ボイラープレートは外部起動済 WildFly に対する適用のみを想定
- `wildfly-maven-plugin` の `execute-commands` は management port 経由で適用する

## 適用タイミング

- VSCode：`dev:bootstrap-and-deploy` タスクの中で `wildfly:apply-config` として実行
- Docker：イメージビルド時に `RUN jboss-cli.sh --file=...` で実行
- 単発：VSCode タスク `wildfly:apply-config` で適用

ローカル WildFly モード（`WILDFLY_HOME` 指定時）は、初回起動時に `scripts/wildfly-local-setup.sh` が管理ユーザと PostgreSQL JDBC モジュールを冪等に整備したうえで standalone.sh を起動し、その後 `wildfly:apply-config` が management port 経由で `wildfly/cli/*.cli` を適用する。

## PostgreSQL JDBC ドライババージョンの同期管理

PostgreSQL JDBC ドライバの jar は 2 か所で参照される。**バージョン更新時は両方を同じ値に保つこと**：

| ファイル                         | 役割                                                                               | バージョン定数                                     |
| -------------------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------------- |
| `docker/dev/wildfly/Dockerfile`  | Docker Compose モード（イメージビルド時に curl で取得し WildFly モジュールへ配置） | `ARG PG_DRIVER_VERSION=42.7.3`                     |
| `scripts/wildfly-local-setup.sh` | ローカル WildFly モード（初回起動時に curl で取得し WildFly モジュールへ配置）     | `PG_DRIVER_VERSION="${PG_DRIVER_VERSION:-42.7.3}"` |

`pom.xml` の `${postgresql.version}` は Flyway プラグイン dependency 用なので別系統。サーバ側のモジュール配置と同期させる必要はないが、開発体験を揃えるため通常は同一値に保つのが望ましい。
