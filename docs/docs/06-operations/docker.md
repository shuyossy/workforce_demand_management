# Docker

本ボイラープレートは **イメージビルドまでをスコープ** とする。レジストリへの push、本番 run、デプロイ手順は適用先プロジェクトの責務。

## マルチステージ構成

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY .mvn ./.mvn
COPY mvnw ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM quay.io/wildfly/wildfly:32.0.1.Final-jdk17 AS runtime
USER root
COPY wildfly/cli/ /opt/jboss/cli/
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/opt/jboss/cli/01-datasource-postgres.cli || true
COPY --from=build /app/target/*.war /opt/jboss/wildfly/standalone/deployments/rcb.war
USER jboss
EXPOSE 8080 9990
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
```

## 起動時 CLI 適用フロー

1. build stage で WAR をビルド（17-jdk）
2. runtime stage で WildFly 32 / JDK 17 ベースを準備
3. CLI スクリプトを `/opt/jboss/cli/` にコピー
4. `jboss-cli.sh --file=` で CLI を適用（datasource 等）
5. WAR を `standalone/deployments/` に配置
6. `standalone.sh` で起動

## イメージビルド

```bash
docker build -t sak-dev-env:latest .
```

CI（GitLab）では Kaniko か `docker buildx` を使う（既存 `.gitlab-ci.yml` 構成）。

## 適用先プロジェクトの責務

- レジストリ push（GitLab Container Registry / ECR / GCR 等）
- 本番 run / Kubernetes Deployment 定義
- 環境変数注入（`DB_URL` / `DB_USER` / `DB_PASSWORD` 等）
- SBOM 生成 / 脆弱性スキャン（`.gitlab-ci.yml` で `syft` / `grype` を組み込み済。適用先で閾値を調整）
