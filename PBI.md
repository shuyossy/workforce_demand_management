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

- PBI名: 開発環境整備
- ステータス: in progress
- 受け入れ基準
  - ローカル開発環境が整備されていること
    - .vscode の整備（settings.json / tasks.json / launch.json を共有）
    - pre-commit / commit-msg / pre-push フックの整備（Husky + lint-staged + commitlint）
  - CI/CD パイプラインが整備されていること
    - .gitlab-ci.yml と .gitlab/ci/\*.yml で stage 分割済み
    - MR パイプラインと main パイプラインの 2 系統
    - Docker イメージのビルド + SBOM 生成 + 脆弱性スキャン
    - docusaurus の GitLab Pages 自動デプロイ
  - Javaの品質担保戦略の標準準拠化（指摘事項対応、2026-04-24）
  - Javaの品質担保戦略の明文化（rules/README.md 作成、指摘事項対応、2026-04-24）
  - Checkstyle 二層構成の非対称性解消（blocking ruleset を pre-push / CI でも再実行、advisory / blocking のファイル名ペア化、指摘事項対応、2026-04-24）
  - 品質ツール（Checkstyle / PMD / SpotBugs / JUnit / JaCoCo）の `mvn site` レポート出力対応と GitLab Pages への `/reports/` 公開（指摘事項対応、2026-04-24）
- 注意事項
- 指摘事項

# ID: 2

- PBI名: サンプルアプリの作成
- ステータス: done
- 受け入れ基準
  - サンプルアプリが作成されていること
    - お題は旅程作成アプリ（Trip + Activity の CRUD）
    - ドメインは`sak.sample`
    - コンテキストパスは`sampleapp`
    - H2DBのインメモリを利用、Spring Data JPA で操作
    - Lombokを利用（@Getter / @Setter / @RequiredArgsConstructor / @Slf4j）
    - トランザクションスクリプトパターンで実装（Service 層に集約）
    - テスト整備（Java: 21 ケース、JavaScript: 3 ケース）
    - フロントエンドは jQuery / Bootstrap を webjars 経由で取得
    - ドキュメント整備（docusaurus 5 章 + intro、mermaid 対応）
    - SpringAI 導入（OpenAI Compatible、mock プロファイルで実 LLM 不要）
      - WeatherTool（@Tool）と SuggestedActivity（structured output）を組み合わせ、
        天気を取得して Activity 提案＋登録する機能を実装
  - 静的解析ツールのチェック内容が最適化されている
    （ボイラープレート方針に沿って構造的衝突を rule property / exclude で TOBE 化、
    意思決定は rules/README.md §4.5 の決定ログに記録）
- 注意事項
  - できるだけシンプルに、コード量少なく作成すること
  - 静的解析を回避するようなアノテーションやコメントをコード内に記載することは禁止
  - 静的解析チェック内容の最適化については、本ボイラープレートを適用したチームが品質とスピードを両立しながら開発できるように実施すること
    - 実装中のサンプルアプリに警告がでないようにその場しのぎでチェック内容を変更するのではなく、理想のTOBEを考えた上でチェック内容を最適化すること
- 指摘事項（in progressの場合のみ）

# ID: 3

- PBI名: Jakarta EE用ブランチの作成
- ステータス: in progress
- 背景
  - 社内でJakarta EEを採用するプロジェクトでも利用できるように本プロジェクトを改良する必要がでてきた
    - また、上記のようなプロジェクトではAIがメインで作業する予定である
  - Jakartta EEのサンプルプロジェクトは次のPBIで作成予定
- 受け入れ基準
  - AIがメインで作業する環境に合わせて開発環境が最適化されており、`jakartaEE`ブランチが作成されていること
    - ※ PBI 原文は `main/jakartaEE` だが、git の ref ストレージ仕様上 `main` ブランチが
      存在する状態では `main/jakartaEE` を作成できない（`refs/heads/main` がファイルと
      ディレクトリの両方を兼ねられない）。実装では `jakartaEE` という単独ブランチで
      代替している（CI の workflow / pages / release ルールも `jakartaEE` を `main`
      と同等に扱うよう調整済み）。
    - 最適化とは例えば、コミットはAI自身で実行させるのでpre-commitもpre-pushくらい厳しくした方がよいのではないか等
      - 差分ファイル単位での Checkstyle google（Google Java Style 全規則 hard gate）と PMD full（PMD 7.x 標準 8 カテゴリ hard gate）を pre-commit に追加。Spotless / Checkstyle blocking / PMD blocking と合わせて 5 段の hard gate 二段化。SpotBugs / JUnit / JaCoCo は差分単位化困難のため pre-push と CI で全体実行を維持
      - 各種チェックツールでAIがミスやバグの温床に早い段階から気付けるようにしたい（シフトレフト）
    - spring bootのサンプルプロジェクトが削除されていること
      - 関連ドキュメントや各種設定を含む（`docs/docs/`、`docs/plans/`、`HELP.md`、`vite.config.js`、`vitest.config.js`、`lombok.config`、Spring Boot 系 dependencies、`spring-boot-maven-plugin`、Dockerfile の Spring Boot 構成）
        - サンプルプロジェクトに最適化されていた静的チェックルール等の各種設定ファイルは、元に戻す（より厳しくする）こと（`checkstyle-advisory.xml` 廃止 → `checkstyle-google.xml` 新設＋ Checker 直下 severity を error 化、PMD ruleset.xml の 13 件の緩和撤回、SpotBugs exclude.xml の 3 件除外撤回、eclipse-formatter.xml の Google 公式版再コピー）
    - AGENT.mdが更新されていること（`AGENTS.md`）
- 注意事項
  - google styleに厳密に沿うようにする
    - checkstyleのチェックルールやフォーマッタ定義は現状少しでも編集されている可能性があるのでコピーし直す（`google_checks.xml` 10.17.0 タグ ＋ `eclipse-java-google-style.xml` を逐語コピー、独自パッチは google_checks の Checker 直下 severity の 1 行のみ）
- 指摘事項（in progressの場合のみ）

# ID: 4

- PBI名: Jakarta EE用サンプルプロジェクトの作成
- ステータス: in progress
- 背景
  - ID:3を参照
- 受け入れ基準
  - Jakarta EE様のサンプルプロジェクトが作成されていること
    - お題は自由（下記要件を満たせるようなお題で、最もシンプルに作成できるものにすること）
      - 採用したお題：休暇申請・承認アプリ（leave）。実装場所は `src/main/java/jp/mufg/it/rcb/leave/`。
    - 要件
      - Java
        - アプリケーションはJava 17（/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home）を利用
        - VSCodeのExtension Pack for JavaはJava 21（/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home）が必要なはずなので注意
      - APサーバ
        - wildfly 32.0.1.Finalを利用
        - datasource等の設定はcli設定ファイルとしてアプリケーションコードと同様にgit管理（`wildfly/cli/01..04-*.cli`、プロジェクトルート直下）
          - データソース名は`PostgresDS`とすること
          - 接続情報は環境変数で管理
        - 開発はローカルorリモートで立ち上げたAPサーバを利用し、本番はDockerコンテナを利用する予定
      - アーキテクチャ
        - ヘキサゴナルアーキテクチャ、クリーンアーキテクチャ（ArchUnit `ArchitectureTest` で hard gate）
          - portはアプリケーション層に配置すること（`leave.application.port.{in,out}`）
        - パッケージ構成は`jp.mufg.it.rcb.~`とすること
      - VSCode
        - 以下を実行できるように`.vscode`を整備
          - デバッグモードでアプリを実行（java,javascript）
            - 常に最新のソースコードの状態が反映されるように注意（`wildfly:redeploy` preLaunchTask）
            - APサーバの設定も最新化する
              - cli設定ファイルの冪等性が必要か？ → CLI 内 `if outcome != success` 構文で冪等化
          - flywayでデータを初期化（開発用にデータ初期化用SQLを用意）
            - 既存のテーブルは全て削除してよいが、確認ダイアログを表示すること（`inputs.promptString` で "yes" 必須）
          - flywayでDBマイグレーション
      - Docker
        - Dockerfileにアプリケーションビルド用の定義を記載（マルチステージ build → wildfly）
      - DB
        - PostgreSQLを利用
        - flywayを利用してマイグレーション（`src/main/resources/db/migration/V1__init.sql`）
      - テスト
        - 単体テスト、結合テスト、E2Eテスト(playwright)、性能テスト(k6)
          - DBを利用する場合はインメモリのH2DBを利用（`persistence-test.xml`）
      - 画面
        - PrimeFacesを利用
        - JSはバンドルせず、各xhtmlファイルに紐づける想定（`<h:outputScript>` 個別読込）
        - ハイクオリティかつ、ユーザのメンタルモデルに沿うような画面とすること
      - ログ
        - `my_work/src/main/java/jp/mufg/it/rcb/log/cdi`をベースに作成
          - 将来的には同じIFを持つ社内用コードに置き換え予定
        - 標準出力に出力する
        - アプリケーション内でロガーを呼び出す際は、以下のようなインターフェースにすること（社内ライブラリと同じIFであり、変更不可）
          - `@InjectLogger`を付与してロガーをインジェクションすること
          - ロギングメッセージは`message.properties`に記載すること（`src/main/resources/message.properties` + `shared.message.MessageIds`）
            - 例：`RCB00001-E=Test error emessage: {0} {1}`
              - キーはメッセージID
                - RCBは固定
                - 00001の部分は連番とすること
                - Eの部分は、E(Error),W(WORN),I(INFORMATION)のいずれか（FINE以下はloggerで直接メッセージを記載）
              - `{0} {1}`のようにパラメータも指定可能
          - 第一引数にログレベル（例：LEVEL.WARNING）を、第二引数にメッセージID（String）またはメッセージ（FINE以下の場合はメッセージIDではなく、直接メッセージ本文を指定可能、String）を、第三引数は任意であり第二引数がメッセージIDかつ対応するメッセージがパラメータ対応の場合はリスト形式でパラメータに埋め込む値を指定すること
      - エラー関連処理
        - `my_work/src/main/java/jp/mufg/it/rcb/exception`をベースに作成
          - 将来的には同じIFを持つ社内用ライブラリに置き換え予定
        - アプリケーション内で業務エラーをthrowする場合は`MSTBusinessException`,`MSTBusinessNonRecoverException`を利用すること
          - `faces-config.xml`でエラーページを制御可能（`web.xml <error-page>` + `error.xhtml`）
          - それ以外のエラーに関するハンドリングが必要になる場合は、適宜相談して方針を決めること
      - ユーザ情報
        - `my_work/src/main/java/jp/mufg/it/rcb/userinfo/dto`をベースに作成
          - 将来的には同じIFを持つ社内用ライブラリに置き換え予定
            - 将来的には社内独自の認証サーバ経由でアクセスされる予定であり、リクエストヘッダから認証情報を取得する予定
              - 本環境では、`UserDto`は認証後のユーザ情報としてセッションで保持しておく（`userinfo.context.UserInfoContext`）
      - 社内独自の認証サーバについて
        - 将来的には社内独自の認証サーバを経由してアクセスされる
          - イメージ
            - アプリ利用者はまず認証サーバにアクセス`https://aaa/${junction-path}/rcb/...`（aaaは認証サーバのFQDN,junction-pathは任意の値が入る）
            - 認証サーバはAPサーバにアクセス`https://bbb/rcb/...`（bbbはAPサーバのFQDN）
          - 画面上のリンク等注意する必要がある（`shared.web.AppUrlBuilder` で外部公開 URL 構築を一元化）
      - その他
        - Lombokを利用
        - AI機能は不要（PBI #3 で SpringAI 系は削除済み）
    - ドキュメントもサンプルアプリに合わせて更新されていること
      - 以下は必ず含めること
        - 要件定義（`docs/docs/02-requirements/`）
        - ADR
          - 重要な項目があれば記載
        - 技術設計書（`docs/docs/05-technical-design.md`、一本化）
          - フォルダ構成
          - ロギング
          - エラーハンドリング
          - データアクセス
  - 静的チェック等の各種設定値がサンプルアプリに合わせて最適化されていること
- 注意事項
  - できるだけシンプルに、コード量少なく作成すること
  - 静的解析を回避するようなアノテーションやコメントをコード内に記載することは禁止
  - 静的解析チェック内容の最適化については、本ボイラープレートを適用したチームが品質とスピードを両立しながら開発できるように実施すること
    - 実装中のサンプルアプリに警告がでないようにその場しのぎでチェック内容を変更するのではなく、理想のTOBEを考えた上でチェック内容を最適化すること
  - `my_work`は社内ライブラリのコピーなので内容を変更せず`src`内で利用すること
    - `src`配置後は、内容変更不可の旨をドキュメントにも反映させておくこと
    - `my_work`内のコードで不足しているクラス等（例:Configクラス）があれば、独自実装して用意すること
      - ただし、独自実装して用意したクラスについては社内ライブラリのコピー以外からは呼び出し不可とすること
    - やむを得ず内部実装を変更する場合についても、利用の際のIFは変えてはいけない（現状の`my_work`内の情報で足りるはずだが、足りない場合は相談してください）
- 指摘事項
  - 技術設計書のバリデーション・認証認可がサンプルアプリにより過ぎているか？（対応済み、2026-05-24：§5 バリデーション 層別責務表・§6 認証認可（特に認可サブセクション）を案件中立な記述に書き換え、leave 固有の業務ルール／認可ロジック（`ApprovalPolicy` 呼び出し、自己承認禁止、`#{leaveDetailBean.canApprove}` 等）は既存規約の `:::note サンプル例` callout に集約。同根の小さなリーク（§3 レベル基準表の `RCB00101-W` 直書き、§4 業務エラー表現ポリシー bullet 内の leave 固有 throw 例）も同時に整理）
  - ボイラープレート適用先プロジェクトがそのまま採用しても開発を進められるような汎用的な内容にする必要がある（対応済み、2026-05-24：「`:::note サンプル例` callout を全削除した状態」でも本文だけで「適用先案件はどう書けばよいか」が成立する構成に再編。承認概念のない案件でも §6 認可 本文の primitives（`UserInfoContext` / `UserDto` / `UserPositionDto`）から実装方針を導ける）
