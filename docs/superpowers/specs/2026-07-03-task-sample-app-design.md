# タスク管理サンプルアプリ（認証なし）設計書

- 日付: 2026-07-03
- 対象PBI: ID:1「サンプルアプリの簡略化」
- ステータス: 設計確定（実装計画へ）

## 1. 背景と目的

本ボイラープレートには「休暇申請サンプル（leave）＋開発用ログイン認証」が同梱されている。適用先で認証機能が不要なケースがあるため、**認証に一切依存しない簡潔なサンプルアプリ**に置き換える。サンプルは Jakarta EE / WildFly / PrimeFaces とクリーンアーキテクチャ／ヘキサゴナルアーキテクチャ、ロギング、例外処理のパターンをデモする役割を担う。

### ゴール

- 現状サンプル（leave）と認証基盤一式を**完全撤去**する。
- 認証非依存の**タスク管理サンプル**（一覧・新規作成・完了の3ユースケース）を新設する。
- ロギング・例外処理を盛り込み、特に**例外処理からのページ遷移を結合テスト／E2Eで実通過**させる。
- 関連ドキュメントを更新する。
- `./mvnw -Pfast clean verify` と docusaurus build がグリーンで、カバレッジ hard gate（本体 85% / `-Pe2e` 95%、LINE/BRANCH）を満たす。

## 2. スコープ

### 2.1 変更してはいけないもの（社内ライブラリ準拠 IF）

`jp.mufg.it.rcb.config` / `log.cdi` / `log.formatter` / `exception.**` / `userinfo.**` は内容変更禁止（pre-commit ガード対象）。`UserInfoContext` / `UserDto` / `UserPositionDto` はファイルとして残るが、**新サンプルからは一切参照しない**。

> 補足: `default.properties` と `faces-config.xml` は「社内ライブラリ準拠 IF のコード」ではなく**アプリ側リソース**であり、技術設計書 §4 が「新規 ErrorCode 切替時は `default.properties` に追記する」と明記しているとおり編集可能。本設計での例外配線修正はこの2ファイル（＋ `error.xhtml`）にのみ触れ、`ExceptionFacesResponseHandler` 等のハンドラコードは変更しない。

### 2.2 削除対象（休暇申請サンプル＋認証基盤）

**Java 本体**

- `domain/**`（LeavePeriod, LeaveRequest, LeaveStatus, LeaveType, service/ApprovalPolicy）
- `application/**`（port.in の Leave 系 11 ファイル, port.out/{ClockPort, LeaveRepositoryPort}, service の Leave 系 5 サービス＋DomainServiceProducer）
- `adapter/out/persistence/**`（LeaveRepositoryAdapter, LeaveRequestEntity, LeaveRequestMapper）
- `adapter/in/web/{LeaveDetailBean, LeaveFormBean, LeaveListBean, LoginBean}`
- `shared/security/**`（AuthenticationPort, DevLoginAuthenticationAdapter, DevUser）
- `shared/web/{AuthenticationFilter, SystemClockAdapter}`（SystemClockAdapter は §2.4 で再導入）
- `shared/config/AppConfig#getManagerLayerCodes()`（leave 承認専用メソッド。AppConfig 自体は §2.4 参照）

**テスト**

- `domain/**`, `application/**` のテスト一式
- `adapter/in/web/{LeaveDetailBeanIT, LeaveFormBeanIT, LeaveListBeanIT, LoginBeanIT}`
- `adapter/out/persistence/{LeaveRepositoryAdapterIT, LeaveRequestMapperTest}`
- `shared/web/SystemClockAdapterTest`
- `shared/security/{DevLoginAuthenticationAdapterTest, DevUserTest}`
- `application/service/support/{FixedClockStub, UserInfoContextFactory}`（新テスト用スタブは task 向けに作り直す）

**リソース／xhtml**

- `src/main/resources/db/migration/{V1__create_leave_request.sql, R__dev_seed.sql}`
- `src/main/webapp/leaves/**`, `src/main/webapp/login.xhtml`
- `src/main/resources/dev-users.yml`
- `src/test/resources/{dev-users-broken-yaml.yml, dev-users-not-a-list.yml}`

**設定・メッセージ**

- `messages.properties`: `RCB00001-I`〜`RCB00003-I`（leave）、`RCB00101-W`（leave）、`RCB00004-I`/`RCB00005-I`（ログイン）、`RCB09001-W`（未認証）を削除（`RCB00001-I`/`RCB00002-I` は task 用に再利用）
- `microprofile-config.properties`: `app.approval.manager-layer-codes`, `app.auth.mode`, `app.dev-users.path` を削除

**ドキュメント** … §11 参照

### 2.3 維持する共通基盤

- `shared/web/AccessLogFilter`（§2.4 で `UserInfoContext` 依存を除去する改変あり）
- `shared/web/AppUrlBuilder` と `AppUrlBuilderTest`
- `exception.handler.**`（FacesExceptionHandler 系）、`faces-config.xml` のハンドラ登録
- `architecture.ArchitectureTest`（ArchUnit 7 ルール）
- `shared.test.{JpaTestSupport, ReflectionTestSupport}`
- `log.formatter.{RcbLogFormatterTest, RcbMessageResolverTest}`
- `shared.config.AppConfig` と `AppConfigTest`（`getManagerLayerCodes()` を削除。`app.external-base-url` アクセサ等が残る場合は維持。空になるなら AppConfig ごと整理するかは実装時に判断し、`AppConfigTest` を追随させる）

### 2.4 新設・改変対象

新設は §3〜§9、共通基盤の改変は §8 に詳述。

### 2.5 その他の残存参照（サンプル完全置換のための掃除）

grep で洗い出した leave/認証の残存参照。実装時にすべて task/認証なしへ更新する。

| ファイル                                      | 参照                                                     | 対応                                                                                   |
| --------------------------------------------- | -------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `src/test/resources/META-INF/persistence.xml` | `<class>...LeaveRequestEntity</class>`                   | `TaskEntity` に差し替え（本番用 `src/main/resources/META-INF/persistence.xml` も同様） |
| `tests/perf/smoke.js`                         | `login.xhtml` / `loginForm` を GET・検証                 | `tasks/list.xhtml` を叩く smoke に書換                                                 |
| `pom.xml`                                     | SnakeYAML 依存（コメント「dev-users.yml の読み込み用」） | dev-users.yml 削除で他に利用が無ければ依存も削除（実装時に他用途が無いことを確認）     |
| `.env`                                        | `APP_AUTH_MODE=DEV_LOGIN`、`login.xhtml` 前提のコメント  | 認証削除に伴い除去・コメント更新                                                       |
| `.env.example`                                | `H2_FILE_PATH=./target/h2/leave-e2e`                     | `task-e2e`（または汎用名）へリネーム。参照する pom / playwright 設定側と整合を取る     |
| `.gitlab/ci/release.yml`                      | `docker login`（レジストリログイン）                     | 認証サンプルとは無関係。対象外                                                         |

> 掃除の網羅確認: 実装完了後に `grep -rniE "leave|dev-users|login\.xhtml|manager-layer|approval|DEV_LOGIN"`（node_modules/target/.git 除外）で残存ゼロを確認する。ただしメッセージ ID プレフィックス `RCB`/`MST`・データソース名 `PostgresDS`・パッケージ名 `jp.mufg.it.rcb` は固定値のため対象外（strip-sample.md §変更してはいけないもの 準拠）。

## 3. アーキテクチャ／パッケージ配置

依存方向・ArchUnit ルールは技術設計書 §1 に完全準拠。新規クラス一覧：

```
domain/model/
  Task                 … タスク集約（id, title, status, createdAt, completedAt）
  TaskStatus           … enum（TODO / DONE）
application/port/in/
  ListTasksUseCase     … 一覧取得 IF
  CreateTaskUseCase    … 新規作成 IF
  CreateTaskCommand    … 作成コマンド DTO（Bean Validation の正本）
  CompleteTaskUseCase  … 完了 IF
  TaskSummary          … 一覧表示用の読みモデル（id, title, status, createdAt, completedAt）
application/port/out/
  TaskRepositoryPort   … 永続化 IF（findAll / findById / save）
  ClockPort            … 現在時刻取得 IF（再導入）
application/service/
  ListTasksService     … @ApplicationScoped
  CreateTaskService    … @ApplicationScoped @Transactional
  CompleteTaskService  … @ApplicationScoped @Transactional
adapter/out/persistence/
  TaskEntity           … JPA Entity
  TaskMapper           … Entity ⇄ Domain 双方向変換
  TaskRepositoryAdapter… TaskRepositoryPort 実装（@PersistenceContext）
adapter/in/web/
  TaskListBean         … @Named @ViewScoped（一覧 + 完了アクション）
  TaskFormBean         … @Named @ViewScoped（新規作成フォーム）
shared/web/
  SystemClockAdapter   … ClockPort 実装（再導入）
webapp/tasks/
  list.xhtml           … 一覧 + 完了ボタン
  new.xhtml            … 新規作成フォーム
resources/db/migration/
  V1__init.sql         … task テーブル DDL（PostgreSQL / H2 両対応の SQL 標準構文）
  R__dev_seed.sql      … 開発・E2E シード（冪等）
```

## 4. ドメインモデル

### 4.1 `Task`（集約ルート）

| フィールド  | 型            | 説明                            |
| ----------- | ------------- | ------------------------------- |
| id          | Long          | 採番済み ID（未永続時 null）    |
| title       | String        | タイトル（必須・最大長は §5.2） |
| status      | TaskStatus    | TODO / DONE                     |
| createdAt   | LocalDateTime | 作成日時（ClockPort 由来）      |
| completedAt | LocalDateTime | 完了日時（未完了時 null）       |

不変条件・振る舞い：

- `Task.create(title, createdAt)` … status=TODO / completedAt=null で生成。title 空は到達したら設計バグ（Bean Validation で事前に弾く前提だが、値オブジェクト境界でも空を拒否）。
- `Task.complete(completedAt)` … TODO のときのみ DONE へ遷移し completedAt を設定。**既に DONE の場合はドメイン不変条件違反として例外**（回復可の業務エラー。UseCase 側で `MSTBusinessException` に翻訳、詳細は §6）。
- `version` 列は持たない。「TODO のみ完了可」の状態チェックが二重完了（stale）を弾くため楽観ロックは不要。

### 4.2 `TaskStatus`

`TODO`（未完了） / `DONE`（完了）。表示ラベルは xhtml 側で `status-#{...}` の CSS クラスにマップ（既存 app.css の status 系を task 用に整理）。

## 5. ユースケースと画面

### 5.1 URL 設計（ブックマーカブル・コンテキストルート相対）

| 画面     | URL                 | 備考                  |
| -------- | ------------------- | --------------------- |
| 一覧     | `/tasks/list.xhtml` | welcome-file の着地先 |
| 新規作成 | `/tasks/new.xhtml`  |                       |

絶対パス禁止。リンクは `<p:menuitem url="#{request.contextPath}/tasks/list.xhtml">` 等、JSF コンポーネント経由。

### 5.2 UC1: 一覧表示（ListTasks）

- `TaskListBean#init()`（`@PostConstruct`）が `ListTasksUseCase#list()` を呼び、`List<TaskSummary>` を取得（初回データ取得＝RSC 的に一括ロード）。
- `list.xhtml` の `p:dataTable` に ID / タイトル / 状態 / 作成日時 / 操作列を表示。
- 操作列の「完了」ボタン（`p:commandButton action="#{taskListBean.complete(row.id)}"`）は **status=TODO の行のみ `rendered`**（UX 上のボタン抑制。UseCase 側で必ず再評価）。
- ログ: 一覧取得は業務イベントとして INFO を出さない（ノイズ回避）。

### 5.3 UC2: 新規作成（CreateTask）

- `new.xhtml` … タイトル入力 + 「作成」ボタン。
- `CreateTaskCommand`（Bean Validation の正本）: `title` に `@NotBlank` / `@Size(max = 100)`（DB の `title VARCHAR(100)` と一致させる）。
- `TaskFormBean#create()` → `CreateTaskUseCase#create(cmd)`。成功で `/tasks/list.xhtml` に redirect。
- バリデーション違反は JSF/Bean Validation 経路で現画面（new.xhtml）に留置し、テンプレートの `globalMessages`（`p:messages id="globalMessages"`）に一本化表示。
- ログ: 作成成功で `RCB00001-I=タスクを作成しました（id={0}）`（INFO）。

### 5.4 UC3: 完了（CompleteTask）

- 一覧の「完了」ボタンから `CompleteTaskUseCase#complete(id)`。
- UseCase フロー（技術設計書 §6 準拠）:
  1. `TaskRepositoryPort#findById(id)` で取得。
  2. **存在しない場合** → §6 の分類に従い `MSTBusinessNonRecoverException`（回復不可＝POST 偽装／データ不整合）を送出。
  3. `Task.complete(now)` を実行。**既に DONE** の場合は `MSTBusinessException`（回復可）を送出。
  4. `save` して INFO ログ `RCB00002-I=タスクを完了しました（id={0}）`。
- 完了後は `TaskListBean` が一覧を再取得（同一 ViewScoped 内で再ロード or redirect。実装時に UX を確定、既存 leave パターンに合わせる）。

## 6. 例外処理設計（受け入れ基準の核心）

技術設計書 §4 の方針（アプリ側は throw のみ、`FacesExceptionHandler` → `ExceptionFacesResponseHandler` が一括処理）に準拠。**4 経路をデモし、E2E／IT で担保**する。

| #   | 経路                                                               | トリガー                             | 期待挙動                                                                       | 検証                                                  |
| --- | ------------------------------------------------------------------ | ------------------------------------ | ------------------------------------------------------------------------------ | ----------------------------------------------------- |
| 1   | 業務エラー（`MSTBusinessException`, 回復可, HTTP 400）             | 二重完了（既に DONE のタスクを完了） | 現画面（list）に留まり `globalMessages` にエラー表示。error.xhtml へ遷移しない | E2E（2 タブ stale）＋ IT（完了 2 回で例外送出を確認） |
| 2   | バリデーションエラー（構造的）                                     | 新規作成でタイトル未入力             | 現画面（new）に留まり `globalMessages` に表示                                  | E2E                                                   |
| 3   | 業務エラー（`MSTBusinessNonRecoverException`, 回復不可, HTTP 500） | 存在しない ID への完了 POST 偽装     | **faces-config `error.page.500` 経由で error.xhtml へ全画面リダイレクト**      | E2E                                                   |
| 4   | error.xhtml 自体                                                   | 上記 3 の着地                        | 戻りリンクから `/tasks/list.xhtml` へ                                          | E2E で戻り導線を確認                                  |

### 6.1 経路 3 の分類根拠（技術設計書との整合）

技術設計書 §4 は「対象データ不存在」を**回復可**の例に挙げるが、これは leave 詳細のように **GET/ブックマークで対象 ID にナビゲートする**通常操作を想定したもの。本サンプルは**削除機能を持たず、一覧に表示された正規のタスクはサーバから消えない**。したがって完了 POST に乗る ID が実在しないのは通常操作では起こり得ず、**POST 偽装／画面と実データの不整合（想定外の状態）＝回復不可**として扱う。この切り分けをドキュメント（§11 の technical-design / glossary）に明記して矛盾を排除する。

> この経路は §6 認可方針「URL 直叩き・POST 偽装でも UseCase 側で必ず再評価して防御する」のデモも兼ねる。

### 6.2 発見された潜在バグと配線修正（アプリ側リソースのみ）

`ExceptionFacesResponseHandler` の実装は 4xx(400 以外)/5xx/想定外時に faces-config.xml の `error.page.{status}` / `error.page.default` エントリを DOM 走査して redirect 先を決めるが、**現状の faces-config.xml に該当エントリが 1 つも無く、`default.properties` に `exceptionhandler.unknown-business.*` も無い**ため、以下が壊れている（leave サンプルは業務エラー＝現画面留置しか踏まないため露見していなかった）：

- `MSTBusinessNonRecoverException`（`ErrorCode.UNKNOWN_BUSINESS` → 文字列 `"unknown-business"`）の httpstatus が `Config.getInt` 未定義キーの既定値 `0` に解決され、リダイレクト分岐に正しく乗らない。
- `getRedirectUrlForStatus` が `error.page.*` を発見できず redirect 先が `null` になる。

**修正内容（ハンドラコードは変更しない）：**

1. `src/main/resources/default.properties` に追記：
   ```properties
   # 回復不可の業務エラー（MSTBusinessNonRecoverException / ErrorCode.UNKNOWN_BUSINESS）
   exceptionhandler.unknown-business.message={0}
   exceptionhandler.unknown-business.httpstatus=500
   # 想定外システム例外（handleUnknownException が ErrorCode.UNKNOWN で参照）
   exceptionhandler.unknown.message={0}
   ```
   （`unknown` 経路の httpstatus はハンドラが 500 をハードコードするため不要。message のみ補完）
2. `src/main/webapp/WEB-INF/faces-config.xml` に `error.page.500` / `error.page.default`（いずれも `/error.xhtml`）のエントリを追加。ハンドラは `document.getElementsByTagName("entry")` で `<entry><key>error.page.500</key><value>/error.xhtml</value></entry>` 形式を走査するため、この形式で配置する。
   - **実装時の確認事項**: faces-config 4.0 スキーマ検証と `<entry>` 島の両立。WildFly デプロイがスキーマ検証で弾かないことを E2E で確認し、必要なら配置位置・検証設定を調整する（§13 リスク）。
3. `src/main/webapp/error.xhtml` の戻りリンクを `outcome="/login"` → `outcome="/tasks/list"` に修正。

### 6.3 業務メッセージの表現

- ユーザ向け文言は `MSTBusinessException` / `MSTBusinessNonRecoverException` のコンストラクタ引数（`errorMessageParams`）で渡す。`default.properties` の `{0}` passthrough テンプレートがそのまま表示する。
  - 例（回復可）: 「既に完了したタスクです」
  - 例（回復不可）: 「指定されたタスクが見つかりません」（`errorMessageParams` に渡す。`logMessageParams` は別途）
- `messages.properties` は JUL ロガー（`RcbLogFormatter`）経由のログ ID 専用。ユーザ向け応答文言は `default.properties` に集約（経路が別）。

## 7. ロギング

技術設計書 §3 準拠。`@InjectLogger(LoggerType.SYSTEM)` の `sysLogger` を各 Service に注入し、`logger.isLoggable` ガード付きで ID を生リテラルで渡す。

`messages.properties`（task 用に整理後）：

```properties
RCB00001-I=タスクを作成しました（id={0}）
RCB00002-I=タスクを完了しました（id={0}）
RCB00001-E=データベースアクセスに失敗しました   # 共通基盤（維持）
# MST* は社内ライブラリ準拠 IF 由来（維持）
```

- 業務イベント（作成/完了）は INFO。
- 回復可の `MSTBusinessException` はフレームワーク自動ログなし・UseCase 明示ログもなし。
- 回復不可の `MSTBusinessNonRecoverException` は `ExceptionLogHandler` が `Level.SEVERE`（`MST00004-E`）で自動出力。UseCase 側で明示ログを書かない（重複防止）。

## 8. 認証削除に伴う共通基盤の改変

| ファイル                              | 改変内容                                                                                                                                                                                                                                           |
| ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `shared/web/AccessLogFilter`          | `UserInfoContext` / `UserDto` の inject と `empNum` の MDC put を除去。`requestId` のみ MDC 格納し、`empNum` は `RcbLogFormatter` のプレースホルダ `-` に委ねる（Formatter は未設定 MDC を `-` 表示する仕様）。既存の access ログ 1 行出力は維持。 |
| `webapp/WEB-INF/web.xml`              | `welcome-file` を `login.xhtml` → `tasks/list.xhtml` に変更（認証が無いため直接着地）。`<error-page>` 群は維持。                                                                                                                                   |
| `webapp/WEB-INF/templates/main.xhtml` | `<title>` を「タスク管理サンプル」に。menubar を「タスク一覧」`/tasks/list.xhtml`・「新規作成」`/tasks/new.xhtml` に置換。`loginBean` 参照（currentUserName / logout facet）を削除。`globalMessages` は維持。                                      |
| `webapp/error.xhtml`                  | §6.2 の戻りリンク修正。                                                                                                                                                                                                                            |

## 9. データアクセス

- `TaskEntity`（`adapter.out.persistence`）と `TaskMapper` で Entity ⇄ Domain を分離（`..domain..` は `jakarta.persistence..` に依存しない、ArchUnit hard gate）。
- `TaskRepositoryAdapter` は `@PersistenceContext EntityManager` で `findAll` / `findById` / `save` を実装。
- トランザクション境界は Service の `@Transactional`（UseCase 境界）。バッキングビーンには付与しない。
- `V1__init.sql`: `task` テーブル（`id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`, `title VARCHAR(100) NOT NULL`, `status VARCHAR(4) NOT NULL` + `CHECK (status IN ('TODO','DONE'))`, `created_at TIMESTAMP NOT NULL`, `completed_at TIMESTAMP`）。PostgreSQL / H2 両対応の SQL 標準構文。
- `R__dev_seed.sql`: DELETE → INSERT で冪等。TODO 数件（一覧・完了 E2E 用）を投入（DONE 行は E2E 側で生成するため必須ではない）。
- persistence.xml から `LeaveRequestEntity` マッピング参照を除去し、`TaskEntity` を反映（実装時に確認）。

## 10. テスト戦略とカバレッジ担保

技術設計書 §8 準拠。**mock 多用を避け**、Repository / Backing Bean は H2 + 本物 Service まで通す。カバレッジ hard gate（本体 85% / `-Pe2e` 95%、LINE/BRANCH、フラット BUNDLE）を満たすよう、新規クラスすべてにテストを対応させる。

### 10.1 クラス → テスト対応表

| 対象クラス              | テスト種別 | テストクラス              | 主な観点                                                                                                    |
| ----------------------- | ---------- | ------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `Task`                  | 単体       | `TaskTest`                | create の初期状態、complete の TODO→DONE、既 DONE 再完了で例外、title 空拒否（境界）                        |
| `TaskStatus`            | 単体       | `TaskStatusTest`          | enum 値・遷移可否                                                                                           |
| `CreateTaskCommand`     | 単体       | `CreateTaskCommandTest`   | Bean Validation（必須・最大長・境界値）                                                                     |
| `CreateTaskService`     | 単体       | `CreateTaskServiceTest`   | port モック＋Clock スタブ。生成・保存・INFO ログ発火                                                        |
| `CompleteTaskService`   | 単体       | `CompleteTaskServiceTest` | 正常完了、既 DONE→`MSTBusinessException`、不存在→`MSTBusinessNonRecoverException`、ログ                     |
| `ListTasksService`      | 単体       | `ListTasksServiceTest`    | 一覧取得・`TaskSummary` マッピング、空一覧                                                                  |
| `TaskMapper`            | 単体       | `TaskMapperTest`          | Entity ⇄ Domain 双方向、null（completedAt）扱い                                                             |
| `SystemClockAdapter`    | 単体       | `SystemClockAdapterTest`  | now() が非 null・現在時刻近傍                                                                               |
| `TaskRepositoryAdapter` | 結合(H2)   | `TaskRepositoryAdapterIT` | save→findById/findAll の往復、採番                                                                          |
| `TaskListBean`          | 結合(H2)   | `TaskListBeanIT`          | seed 読取、complete アクションで status 遷移、既 DONE 再完了で業務例外経路（FacesContext static mock のみ） |
| `TaskFormBean`          | 結合(H2)   | `TaskFormBeanIT`          | create→`em.clear()`→検索で永続化確認、`@Valid` 経路                                                         |

- 単体の Clock は `FixedClockStub`（決定性確保）を task 用に用意。
- Backing Bean IT は本物 Service ＋本物 `TaskRepositoryAdapter` ＋ H2、`FacesContext` のみ `Mockito.mockStatic`（`@BeforeEach`/`@AfterEach` で開始・close 徹底）。
- `ArchitectureTest`（ArchUnit）は維持され、新クラスも層境界 hard gate に自動適合。

### 10.2 E2E（Playwright）

`tests/e2e/tests/` を task 用に全面書換：

| spec                     | シナリオ                                                                                                                                                                                                                     | 担保する経路                                       |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| `golden-path.spec.ts`    | 新規作成 → 一覧に表示 → 完了 → status=DONE 表示                                                                                                                                                                              | 正常系フルスタック（CDI / JSF / Filter / TX 配線） |
| `error-handling.spec.ts` | (a) タイトル未入力→new に留置＋globalMessages / (b) 2 タブ stale 二重完了→list に留置＋業務メッセージ（error.xhtml へ遷移しない）/ (c) 存在しない ID へ完了 POST 偽装→error.xhtml へリダイレクト＋戻りリンクで `/tasks/list` | 例外 3 経路（受け入れ基準の核心）                  |

- E2E は認証が無いため、既存の `login()` ヘルパを撤去し各 spec を簡素化。
- (c) の POST 偽装は Playwright で完了アクションの hidden id パラメータを存在しない値に差し替えて送信、または相当のリクエストを直接発行して再現。
- E2E カバレッジは `-Pe2e` で JaCoCo に統合し 95% 閾値を再評価。

## 11. ドキュメント更新

| ファイル                                                                                    | 対応                                                                                                                                                                         |
| ------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docs/docs/02-requirements/domain-glossary.md`                                              | 用語集をタスク管理（Task / TaskStatus / 完了 / 業務エラー等）に全面刷新。AGENTS.md から参照される正本                                                                        |
| `docs/docs/01-getting-started/strip-sample.md`                                              | 「タスクサンプルの削除手順＋認証は既に無い」前提へ書換。削除対象パス・残るスモークテスト表を task/認証なしに更新                                                             |
| `docs/docs/04-domain-design/sample/**`                                                      | leave ドメイン設計を task に差し替え（or 該当ファイル再作成）                                                                                                                |
| `docs/docs/04-domain-design/01-domain-model.md` / `02-usecases.md` / `03-business-rules.md` | 各 §サンプル例を task に差し替え                                                                                                                                             |
| `docs/docs/05-technical-design.md`                                                          | §サンプル例（クラス配置 / テーブル定義 / 採番 / 部長層コード）と「（サンプル）」注釈を task に差し替え。§4 に §6.1 の「回復可/回復不可の対象不存在の切り分け」を追記して整合 |
| `docs/docs/03-adrs/ADR-002-snapshot-vs-port.md`                                             | leave 固有（スナップショット方式）につき削除                                                                                                                                 |
| `docs/docs/03-adrs/ADR-001-clean-hexagonal.md`                                              | §サンプル例（leave）を task に差し替え                                                                                                                                       |
| `docs/docs/intro.md`                                                                        | 「次のステップ」のサンプル導線を更新                                                                                                                                         |
| `docs/sidebars.js`                                                                          | ADR-002 と leave sample entry を除去／task に更新                                                                                                                            |
| `README.md`                                                                                 | サンプル説明セクションを task に更新                                                                                                                                         |
| `PBI.md`                                                                                    | ID:1 を done に更新                                                                                                                                                          |

## 12. 完了条件（DoD）

- 現状サンプル（leave）＋認証基盤が完全に削除されている。
- 認証不要のタスク管理サンプル（一覧・新規作成・完了）が新設され、ロギング・例外処理を含む。
- 例外処理からのページ遷移（現画面留置＋error.xhtml リダイレクト）が E2E で実通過し、二重完了の業務例外が IT でも担保されている。
- `scripts/with-env.sh ./mvnw -Pfast clean verify` がグリーン。
- カバレッジ hard gate（本体 85% / 可能なら `-Pe2e` 95%）を満たす。
- `cd docs && npm run build` がグリーン。
- 関連ドキュメントが更新されている。
- ビルド確認後 commit / push まで完了。

## 13. リスク・実装時確認事項

- **faces-config.xml の `<entry>` 島とスキーマ検証**: `ExceptionFacesResponseHandler` が期待する `<entry><key>/<value>` 形式を faces-config 4.0（schemaLocation 付き）に追加した際、WildFly デプロイがスキーマ検証で弾かないか E2E で確認。弾く場合は配置位置調整・検証緩和を検討（ハンドラコードは不変更のため設定側で吸収）。
- **AppConfig の残存メンバ**: `getManagerLayerCodes()` 削除後に AppConfig が空になるか。空なら整理方針（クラス維持 or 削除）を決め、`AppConfigTest` を追随。
- **persistence.xml のマッピング**: `LeaveRequestEntity` 参照を除去し `TaskEntity` を反映。
- **完了後の一覧再表示 UX**: ViewScoped 内再取得 or redirect のどちらにするか、既存 leave パターンに合わせて実装時確定。
- **クリーンアーキ順**: 実装は中心（domain）→外側（application → adapter → web/リソース）の順、TDD（テスト先行）で進める。

```

```
