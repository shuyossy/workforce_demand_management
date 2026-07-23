# Docker

本ボイラープレートは **イメージビルドまでをスコープ** とする。レジストリへの push、本番 run、デプロイ手順は適用先プロジェクトの責務。

## マルチステージ構成（プロジェクトルート `Dockerfile` の要点）

```dockerfile
# Stage 1: build — Maven で WAR を生成（依存解決レイヤを分離してキャッシュ活用）
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:resolve
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

# Stage 2: runtime — WildFly 32 / JDK 17
FROM quay.io/wildfly/wildfly:32.0.1.Final-jdk17 AS runtime
USER root
# PostgreSQL JDBC モジュール配置（01-datasource-postgres.cli の driver-module-name を解決）
ARG PG_DRIVER_VERSION=42.7.3
RUN mkdir -p .../org/postgresql/main && curl -fsSL .../postgresql-${PG_DRIVER_VERSION}.jar ...
# CLI スクリプト + entrypoint 配置
COPY wildfly/cli/ /opt/jboss/cli/
COPY scripts/docker-entrypoint.sh /opt/jboss/docker-entrypoint.sh
# web.xml の ${env.*:default} 置換を build 時に standalone.xml へ焼き込み（embed-server）
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/opt/jboss/cli/05-ee-descriptor-replacement.cli
COPY --from=build /app/target/rcb-*.war /opt/jboss/wildfly/standalone/deployments/rcb.war
# root 実行の embed-server が残す root 所有ファイルを実行ユーザへ戻す（boot 時 Permission denied 対策）
RUN chown -R jboss:root /opt/jboss/wildfly/standalone
USER jboss
EXPOSE 8080 9990
ENTRYPOINT ["/opt/jboss/docker-entrypoint.sh"]
```

## 起動フロー（`scripts/docker-entrypoint.sh`）

1. 必須環境変数（`DB_URL` / `DB_USER` / `DB_PASSWORD`）を検証。未設定なら明示エラーで即停止
2. `APP_CONTEXT_ROOT` 未指定時は `/rcb` を既定値として export
3. `01-datasource-postgres.cli` 〜 `04-proxy-forwarding.cli` を 1 つの `embed-server` セッションに連結し、**WildFly 起動前に offline 適用**（standalone.xml へ永続化。CLI は online 実行（ローカル開発/E2E の wildfly-maven-plugin 経由）と共用の冪等スクリプト）
4. `standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0` で起動 → `standalone/deployments/rcb.war` が boot 時 auto-deploy

boot 前のコンテナには online CLI の接続先が存在しないため、embed-server を介さない `jboss-cli.sh --file` 実行は必ず失敗する点に注意（過去にこの経路で datasource 未登録のまま起動する不具合があった）。

## イメージビルドと起動

```bash
DOCKER_BUILDKIT=1 docker build -t rcb:local .

# PostgreSQL は別途用意し、Flyway migrate 済であること
docker run --rm -p 8080:8080 -p 9990:9990 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/rcb \
  -e DB_USER=rcb -e DB_PASSWORD=rcb \
  rcb:local
```

CI（GitLab）では Kaniko か `docker buildx` を使う（既存 `.gitlab-ci.yml` 構成）。

## スキーマ管理（Flyway）との関係

イメージは Flyway を内蔵しない。スキーマ適用はデプロイパイプライン側の責務（例: `DB_URL=... ./mvnw flyway:migrate` を先行実行）。`db/dev-bootstrap`（開発/E2E seed）は既定 locations 外のため本番では流れない。

## 適用先プロジェクトの責務

- レジストリ push（GitLab Container Registry / ECR / GCR 等）
- 本番 run / Kubernetes Deployment 定義
- 環境変数注入（`DB_URL` / `DB_USER` / `DB_PASSWORD`、必要に応じ `APP_CONTEXT_ROOT` / `APP_JSF_PROJECT_STAGE` / `APP_SESSION_COOKIE_SECURE` / `APP_SESSION_TIMEOUT_MINUTES`）
- SBOM 生成 / 脆弱性スキャン（`.gitlab-ci.yml` で `syft` / `grype` を組み込み済。適用先で閾値を調整）
