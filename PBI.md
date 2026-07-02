雛形

```
# ID:
- PBI名:
- ステータス: [to do/in progress/done]
- ユーザストーリー/背景
- 受け入れ基準
- 注意事項
- 指摘事項（in progressの場合のみ）
```

# ID: 1

- PBI名: アプリ設定方法見直し
- ステータス: done
- 背景
  - 現状は環境変数と`microprofile-config.properties`両方でアプリの設定を変更可能
- 受け入れ基準
  - アプリの設定は環境変数か`microprofile-config.properties`にできるだけ寄せることができている
  - 設定値が重複管理されていない
- 注意事項
- 指摘事項（in progressの場合のみ）

# ID: 2

- PBI名: 開発・本番の設定値が混合しないようにする
- ステータス: done
- 背景
  - `WEB-INF/web.xml`で開発で用いるべき値が固定されているのを発見
    - `jakarta.faces.PROJECT_STAGE`とcookieの設定
      - cookieについては、本番がHTTPSでアクセスされる可能性もある（まだ決定ではない、HTTPSで認証リバプロにアクセス、HTTPSでアプリに直接アクセス、HTTPでアプリに直接アクセスなど様々な可能性がある）
- 受け入れ基準
  - `WEB-INF/web.xml`含め全体見直して開発・本番の設定値が混合していないか調査できている
    - 調査の結果によって適切な場所で設定値を指定できるように修正されている
- 注意事項
- 解決サマリ
  - 全体調査の結果、開発固定値は `WEB-INF/web.xml` の `jakarta.faces.PROJECT_STAGE=Development` と セッション Cookie `<secure>false</secure>` の 2 点に集約されていた（他の記述子・properties に dev/prod 混在は無し）。
  - この 2 値（+ 任意で `session-timeout`）を `${env.APP_JSF_PROJECT_STAGE:Production}` / `${env.APP_SESSION_COOKIE_SECURE:true}` / `${env.APP_SESSION_TIMEOUT_MINUTES:30}` にパラメータ化。`@ConfigProperty` の `app.*` ではなく `APP_CONTEXT_ROOT` と同じ「インフラ/デプロイ値」カテゴリと整理し、WAR 内既定を本番安全側（Production / secure=true）に固定。開発は `.env` の `APP_*` で dev 値へ上書き。
  - 展開に必要な WildFly `ee` サブシステムの `spec-descriptor-property-replacement=true` を、ローカル/E2E は `wildfly/cli/02-system-properties.cli`（online）、Docker（本番・開発イメージ）は `wildfly/cli/05-ee-descriptor-replacement.cli` の build 時 bake で有効化。cookie `secure` は本番 TLS 終端トポロジが未確定のため環境変数駆動のまま残置。
  - `.env.example` / `docs/docs/01-getting-started/env-setup.md` / `docs/docs/05-technical-design.md` を更新。
  - 別課題として `scripts/docker-entrypoint.sh` の runtime CLI（データソース登録含む）が「サーバ未起動・embed-server 無し」で実質未機能である既存不備を発見（設定「適用機構」の問題）。本 PBI の範囲外のため別 PBI として下記に起票。
- 指摘事項（in progressの場合のみ）

# ID: 3

- PBI名: 本番 Docker の CLI 設定適用（データソース登録）が機能していない不備の解消
- ステータス: to do
- 背景
  - ID:2 の調査中に発見。`scripts/docker-entrypoint.sh` は `wildfly/cli/01-datasource-*.cli` 等を「起動中サーバ無し・`embed-server` 無し」で `jboss-cli.sh --file=` 実行しており、管理コントローラへ接続できず `|| true` で握り潰されている。結果として本番 Docker イメージではデータソース（`java:/PostgresDS`）が登録されず、アプリが起動できない。
  - `docs/docs/01-getting-started/env-setup.md` は本番を「build 時に offline CLI で焼き込み（イメージ内 standalone.xml 完成済）」と説明しているが、実装は runtime での適用になっており記述と乖離している。
  - なお接続情報（`DB_URL` 等）は runtime 環境変数依存のため build 時 bake は不可。runtime で `embed-server`（env 解決可）を使うか、サーバ起動後に online 適用するかの設計判断が必要。
- 受け入れ基準
  - 本番 Docker イメージ起動時にデータソース・system property・logging・proxy-forwarding の各 CLI が確実に適用され、アプリが正常起動する
  - ドキュメントの説明（適用タイミング・永続化先）が実装と一致している
- 注意事項
  - ID:2 で導入した `spec-descriptor-property-replacement` の build 時 bake（`05-ee-descriptor-replacement.cli`）は env 非依存のため現状のまま維持でよい（本 PBI は env 依存の runtime CLI 適用が対象）。
- 指摘事項（in progressの場合のみ）
