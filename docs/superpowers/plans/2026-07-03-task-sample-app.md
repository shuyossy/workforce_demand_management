# タスク管理サンプルアプリ（認証なし）実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 休暇申請サンプル（leave）＋開発用ログイン認証を完全撤去し、認証非依存のタスク管理サンプル（一覧・新規作成・完了）へ置き換える。

**Architecture:** クリーンアーキテクチャ／ヘキサゴナルアーキテクチャ。`domain`（中心）→ `application`（port.in/out + service）→ `adapter`（out.persistence / in.web）の一方向依存。例外は `FacesExceptionHandler` → `ExceptionFacesResponseHandler` が一括処理し、アプリ側は throw のみ。TDD・中心から外側の順で実装する。

**Tech Stack:** Jakarta EE 10 / WildFly / PrimeFaces 14 / JSF (Faces) / JPA (Hibernate) / Flyway / Lombok / JUnit5 / Mockito / AssertJ / Playwright / H2（テスト・E2E）/ PostgreSQL（本番）。

## Global Constraints

- **パッケージ**: `jp.mufg.it.rcb`（システムコード `rcb`）。
- **社内ライブラリ準拠 IF（内容変更禁止・pre-commit ガード対象）**: `jp.mufg.it.rcb.config` / `log.cdi` / `log.formatter` / `exception.**` / `userinfo.**`。これらのファイルは一切編集しない（`default.properties` / `faces-config.xml` / `error.xhtml` はアプリ側リソースなので編集可）。
- **`UserInfoContext` / `UserDto` / `UserPositionDto`** はファイルとして残すが、新コードから **一切参照しない**。
- **時刻型は `java.time.Instant`**（`ClockPort.now()` が返す型。`LocalDateTime` は使わない）。
- **コメントは日本語**、**Google Java Style 厳守**（git hook でチェック）。静的解析の抑制は理由コメント必須。
- **URL は絶対パス禁止**。JSF コンポーネント（`<p:menuitem url="#{request.contextPath}/...">` 等）を使う。
- **ユーザ向けメッセージ**は `MSTBusinessException` / `MSTBusinessNonRecoverException` のコンストラクタ引数（`default.properties` の `{0}` passthrough）で渡す。**ログ ID**（`RCB*`）は `messages.properties` が正本で `logger.log(Level.X, "RCBxxxxx-Y", new Object[]{...})` の生リテラルで渡す。
- **カバレッジ hard gate**: 本体 85% / `-Pe2e` 95%（LINE/BRANCH、フラット BUNDLE）。全新規クラスにテストを対応させる。
- **push は最終タスク（Task 18）でのみ行う**。中間コミットはローカルのみ（pre-commit は静的解析＋保護パスガード、カバレッジ hard gate は pre-push/CI で評価されるため、機能・テストが揃う最終段階まで push しない）。中間コミットで `--no-verify` は使わない。
- **保持する汎用資産（削除しない）**: `application/port/out/ClockPort.java`、`shared/web/SystemClockAdapter.java`（＋`SystemClockAdapterTest`）、`shared/web/AppUrlBuilder.java`（＋`AppUrlBuilderTest`）、`shared/web/AccessLogFilter.java`（改変あり）、`shared/test/{JpaTestSupport,ReflectionTestSupport}`、`application/service/support/FixedClockStub.java`（task テストで再利用）、`architecture/ArchitectureTest.java`。
- **検証コマンド**: 単体は `scripts/with-env.sh ./mvnw -Pfast test`、単体+結合は `scripts/with-env.sh ./mvnw -Pfast clean verify`、E2E 込みは `scripts/with-env.sh ./mvnw -Pe2e verify`。

---

## Phase 1: 撤去（leave + 認証を削除し、コンパイル可能な骨格へ）

### Task 1: leave / 認証コードとテスト・リソースを削除し、共通基盤の参照を修正する

**Files:**

- Delete（main Java）:
  - `src/main/java/jp/mufg/it/rcb/domain/model/{LeavePeriod,LeaveRequest,LeaveStatus,LeaveType}.java`
  - `src/main/java/jp/mufg/it/rcb/domain/service/ApprovalPolicy.java`
  - `src/main/java/jp/mufg/it/rcb/application/port/in/` 配下の leave 12 ファイル（ApplyLeaveCommand, ApplyLeaveResult, ApplyLeaveUseCase, ApproveLeaveCommand, ApproveLeaveUseCase, FindLeaveUseCase, LeaveRequestDetail, LeaveRequestSummary, ListLeavesCommand, ListLeavesUseCase, RejectLeaveCommand, RejectLeaveUseCase）
  - `src/main/java/jp/mufg/it/rcb/application/port/out/LeaveRepositoryPort.java`（**ClockPort.java は残す**）
  - `src/main/java/jp/mufg/it/rcb/application/service/{ApplyLeaveService,ApproveLeaveService,FindLeaveService,ListLeavesService,RejectLeaveService,DomainServiceProducer}.java`
  - `src/main/java/jp/mufg/it/rcb/adapter/out/persistence/{LeaveRepositoryAdapter,LeaveRequestEntity,LeaveRequestMapper}.java`
  - `src/main/java/jp/mufg/it/rcb/adapter/in/web/{LeaveDetailBean,LeaveFormBean,LeaveListBean,LoginBean}.java`
  - `src/main/java/jp/mufg/it/rcb/shared/security/{AuthenticationPort,DevLoginAuthenticationAdapter,DevUser}.java`（`shared/security` フォルダごと）
  - `src/main/java/jp/mufg/it/rcb/shared/web/AuthenticationFilter.java`
- Delete（test Java）:
  - `src/test/java/jp/mufg/it/rcb/domain/`（フォルダごと）
  - `src/test/java/jp/mufg/it/rcb/application/`（フォルダごと。**FixedClockStub は Task 2 前に復元するため下記手順で退避**）
  - `src/test/java/jp/mufg/it/rcb/adapter/in/web/{LeaveDetailBeanIT,LeaveFormBeanIT,LeaveListBeanIT,LoginBeanIT}.java`
  - `src/test/java/jp/mufg/it/rcb/adapter/out/persistence/{LeaveRepositoryAdapterIT,LeaveRequestMapperTest}.java`
  - `src/test/java/jp/mufg/it/rcb/shared/security/{DevLoginAuthenticationAdapterTest,DevUserTest}.java`（`shared/security` フォルダごと）
- Delete（リソース）:
  - `src/main/resources/db/migration/{V1__create_leave_request.sql,R__dev_seed.sql}`
  - `src/main/resources/dev-users.yml`
  - `src/test/resources/{dev-users-broken-yaml.yml,dev-users-not-a-list.yml}`
  - `src/main/webapp/leaves/`（フォルダごと）、`src/main/webapp/login.xhtml`
- Modify:
  - `src/main/java/jp/mufg/it/rcb/shared/web/AccessLogFilter.java`
  - `src/main/java/jp/mufg/it/rcb/shared/config/AppConfig.java`
  - `src/test/java/jp/mufg/it/rcb/shared/config/AppConfigTest.java`
  - `src/main/resources/META-INF/microprofile-config.properties`
  - `src/main/resources/messages.properties`
  - `src/main/webapp/WEB-INF/web.xml`
  - `src/main/webapp/WEB-INF/templates/main.xhtml`
  - `src/main/webapp/error.xhtml`
  - `src/test/resources/META-INF/persistence.xml`

**Interfaces:**

- Produces: コンパイル可能な骨格。`AppConfig` から `getManagerLayerCodes()` が消え、`AccessLogFilter` が `UserInfoContext` に依存しない状態。以降の Task が `domain` / `application` / `adapter` に task クラスを追加していく空きスペース。

- [ ] **Step 1: FixedClockStub を退避してから leave/認証コードを一括削除**

FixedClockStub は task テストで再利用するため、`application/` テストフォルダ削除の前に退避する。

```bash
cd /Users/yoshidashuhei/Documents/vscode_workspace/workforce_demand_management
# FixedClockStub を一時退避
cp src/test/java/jp/mufg/it/rcb/application/service/support/FixedClockStub.java /tmp/FixedClockStub.java.keep

# main Java（leave / 認証）
rm src/main/java/jp/mufg/it/rcb/domain/model/LeavePeriod.java \
   src/main/java/jp/mufg/it/rcb/domain/model/LeaveRequest.java \
   src/main/java/jp/mufg/it/rcb/domain/model/LeaveStatus.java \
   src/main/java/jp/mufg/it/rcb/domain/model/LeaveType.java \
   src/main/java/jp/mufg/it/rcb/domain/service/ApprovalPolicy.java
rm src/main/java/jp/mufg/it/rcb/application/port/in/*.java
rm src/main/java/jp/mufg/it/rcb/application/port/out/LeaveRepositoryPort.java
rm src/main/java/jp/mufg/it/rcb/application/service/*.java
rm -rf src/main/java/jp/mufg/it/rcb/adapter/out
rm src/main/java/jp/mufg/it/rcb/adapter/in/web/LeaveDetailBean.java \
   src/main/java/jp/mufg/it/rcb/adapter/in/web/LeaveFormBean.java \
   src/main/java/jp/mufg/it/rcb/adapter/in/web/LeaveListBean.java \
   src/main/java/jp/mufg/it/rcb/adapter/in/web/LoginBean.java
rm -rf src/main/java/jp/mufg/it/rcb/shared/security
rm src/main/java/jp/mufg/it/rcb/shared/web/AuthenticationFilter.java

# test Java
rm -rf src/test/java/jp/mufg/it/rcb/domain
rm -rf src/test/java/jp/mufg/it/rcb/application
rm -rf src/test/java/jp/mufg/it/rcb/shared/security
rm src/test/java/jp/mufg/it/rcb/adapter/in/web/LeaveDetailBeanIT.java \
   src/test/java/jp/mufg/it/rcb/adapter/in/web/LeaveFormBeanIT.java \
   src/test/java/jp/mufg/it/rcb/adapter/in/web/LeaveListBeanIT.java \
   src/test/java/jp/mufg/it/rcb/adapter/in/web/LoginBeanIT.java
rm src/test/java/jp/mufg/it/rcb/adapter/out/persistence/LeaveRepositoryAdapterIT.java \
   src/test/java/jp/mufg/it/rcb/adapter/out/persistence/LeaveRequestMapperTest.java

# リソース
rm src/main/resources/db/migration/V1__create_leave_request.sql \
   src/main/resources/db/migration/R__dev_seed.sql
rm src/main/resources/dev-users.yml
rm src/test/resources/dev-users-broken-yaml.yml \
   src/test/resources/dev-users-not-a-list.yml
rm -rf src/main/webapp/leaves
rm src/main/webapp/login.xhtml

# FixedClockStub を復元（task テストで再利用）
mkdir -p src/test/java/jp/mufg/it/rcb/application/service/support
cp /tmp/FixedClockStub.java.keep src/test/java/jp/mufg/it/rcb/application/service/support/FixedClockStub.java
```

- [ ] **Step 2: `AccessLogFilter` から `UserInfoContext` 依存を除去**

`empNum` の MDC 格納をやめ、`RcbLogFormatter` のプレースホルダ `-` に委ねる。`UserInfoContext` / `UserDto` の import・inject を削除する。ファイル全体を以下に置き換える。

```java
package jp.mufg.it.rcb.shared.web;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;
import org.jboss.logging.MDC;

/**
 * アクセスログ用フィルタ. リクエスト 1 件ごとに {@code requestId} を MDC へ注入し、終端で 1 行のアクセスログを INFO で出力する.
 *
 * <p>{@code requestId} は 8 桁の UUID 前置で生成する。認証機能を持たないサンプル構成のため {@code empNum} は MDC に
 * 格納せず、{@code RcbLogFormatter} のプレースホルダ {@code -} に委ねる。終了時は finally 句で MDC を必ず除去し、
 * スレッド再利用時の汚染を防ぐ。LoggerType はプロジェクト 2 値運用 (SYSTEM / ACCESS) に合わせて SYSTEM を使用。
 */
@WebFilter(filterName = "AccessLogFilter", urlPatterns = "/*")
public class AccessLogFilter implements Filter {

  /** {@code requestId} の桁数（UUID 前置の短縮版）. */
  private static final int REQUEST_ID_LENGTH = 8;

  /** アクセスログ用 Logger（プロジェクトの 2 値運用に合わせて SYSTEM を流用）. */
  @Inject
  @InjectLogger(LoggerType.SYSTEM)
  /* default */ Logger accessLogger;

  /** デフォルトコンストラクタ（Servlet 仕様により public 引数なしが必要）. */
  public AccessLogFilter() {
    // Servlet コンテナがインスタンス化するため初期化処理は不要.
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) request;
    final String requestId = UUID.randomUUID().toString().substring(0, REQUEST_ID_LENGTH);
    final long start = System.currentTimeMillis();
    MDC.put("requestId", requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      final long elapsed = System.currentTimeMillis() - start;
      // PMD GuardLogStatement: 重い String.format を回避するため Level ガードを挟む.
      if (accessLogger.isLoggable(Level.INFO)) {
        accessLogger.info(
            String.format("%s %s elapsed=%dms", req.getMethod(), req.getRequestURI(), elapsed));
      }
      MDC.remove("requestId");
    }
  }
}
```

- [ ] **Step 3: `AppConfig` から `getManagerLayerCodes()` を削除**

`AppConfig.java` を開き、`app.approval.manager-layer-codes` を注入するフィールドと `getManagerLayerCodes()` メソッド、それに付随する import（`java.util.Set` / `Arrays` / `Collectors` など該当分のみ）を削除する。`app.external-base-url` など他のメンバがあればそのまま残す。メソッドが全て消えて空になる場合もクラス自体は残す（`@ApplicationScoped` の設定ラッパーとして骨格に必要）。

Run: `grep -n "manager-layer\|getManagerLayerCodes\|approval" src/main/java/jp/mufg/it/rcb/shared/config/AppConfig.java`
Expected: 削除後は該当行なし（0 件）。

- [ ] **Step 4: `AppConfigTest` から manager-layer 検証を削除**

`AppConfigTest.java` の `getManagerLayerCodes()` を検証するテストメソッドと関連 import を削除する。残る検証（`app.external-base-url` 等）があればそのまま。テストが空になる場合は、残す設定項目の最小検証を 1 つ残すか、`AppConfig` に検証対象が無ければクラスごと削除して構わない（Step 3 の結果に合わせる）。

Run: `grep -n "manager-layer\|getManagerLayerCodes" src/test/java/jp/mufg/it/rcb/shared/config/AppConfigTest.java`
Expected: 0 件。

- [ ] **Step 5: `microprofile-config.properties` から認証・承認キーを削除**

`app.approval.manager-layer-codes` / `app.auth.mode` / `app.dev-users.path` の行と関連コメントを削除する。`app.external-base-url` は残す。

Run: `grep -nE "manager-layer|auth.mode|dev-users" src/main/resources/META-INF/microprofile-config.properties`
Expected: 0 件。

- [ ] **Step 6: `messages.properties` を task 用に整理**

ファイルを以下に置き換える（leave/ログイン/未認証の ID を削除、task 用 `RCB00001-I`/`RCB00002-I` を定義、共通基盤 `RCB00001-E` と `MST*` は保持）。

```properties
# 業務イベント (INFO)
# タスク管理サンプル（task）用。
RCB00001-I=タスクを作成しました（id={0}）
RCB00002-I=タスクを完了しました（id={0}）

# システム障害 (ERROR)
RCB00001-E=データベースアクセスに失敗しました

# 社内ライブラリ準拠 IF（jp.mufg.it.rcb.exception 配下）から出される共通メッセージ
# 本来は社内ライブラリ本体の properties に格納される想定。本ボイラープレートでは
# サンプル用に同梱しており、社内ライブラリ置換時には本セクションを削除する。
MST00003-E=想定外のエラーが発生しました
MST00004-E=想定外の業務エラーが発生しました
MST00005-I=リクエストが無効です
MST00006-I=操作が無効です
MST00007-I=ページが見つかりません
MST00008-I=セッションの有効期限が切れました
MST02001-I=ユーザ情報の取得に失敗しました
MST03001-I=APIは現在受付を停止しています
MST03003-E=外部システムでエラーが発生しました
MST03004-E=外部アプリケーションエラーが発生しました
MST03005-E=外部システムの応答が処理できませんでした
MST04002-I=認証が必要です
MST04101-E=不正な引数が指定されました
MST05001-E=入力内容にエラーがあります
MST05002-E=データの変換に失敗しました
MST05010-E=スタックトレース: {0}
MST11002-I=一意キーが重複しています
MST17005-I=設定の読み込みに失敗しました
```

- [ ] **Step 7: `web.xml` の welcome-file を変更**

`<welcome-file>login.xhtml</welcome-file>` を `<welcome-file>tasks/list.xhtml</welcome-file>` に置き換える。`<error-page>` 群・その他はそのまま。

```xml
  <welcome-file-list>
    <welcome-file>tasks/list.xhtml</welcome-file>
  </welcome-file-list>
```

- [ ] **Step 8: `main.xhtml` を task 用に修正**

`<title>` を「タスク管理サンプル」に、menubar を task メニューに置換し、`loginBean` 参照の facet を削除する。`<h:head>`〜`<p:menubar>` 部分を以下に置き換える（`globalMessages` と `<ui:insert>` 部分は既存のまま残す）。

```xml
    <h:head>
      <title>タスク管理サンプル</title>
      <h:outputStylesheet library="css" name="app.css" />
    </h:head>
    <h:body>
      <!--
        PrimeFaces + server state saving では menubar 内の url ナビゲーションも form ancestor を
        要求するため、menubar 全体を topForm で包む。各ページ側 form は content 内（topForm 外）に展開される。
      -->
      <h:form id="topForm">
        <p:menubar>
          <p:menuitem value="タスク一覧" url="#{request.contextPath}/tasks/list.xhtml" />
          <p:menuitem value="新規作成" url="#{request.contextPath}/tasks/new.xhtml" />
        </p:menubar>
      </h:form>

      <p:messages id="globalMessages" showDetail="true" closable="true" />

      <div class="content">
        <ui:insert name="content" />
      </div>
    </h:body>
```

- [ ] **Step 9: `error.xhtml` の戻りリンクを修正**

`<h:link value="ログイン画面へ戻る" outcome="/login" />` を以下に置き換える。

```xml
        <h:link value="タスク一覧へ戻る" outcome="/tasks/list" />
```

- [ ] **Step 10: テスト用 `persistence.xml` の Entity 参照を差し替え**

`src/test/resources/META-INF/persistence.xml` の `<class>jp.mufg.it.rcb.adapter.out.persistence.LeaveRequestEntity</class>` を `<class>jp.mufg.it.rcb.adapter.out.persistence.TaskEntity</class>` に置き換える（TaskEntity は Task 9 で作成。XML 文字列参照のため本 Step 時点では未存在でも骨格ビルドに影響しない）。本番用 `src/main/resources/META-INF/persistence.xml` は `exclude-unlisted-classes=false` で明示クラス列挙が無いため変更不要。

- [ ] **Step 11: コンパイルと残存テストの確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test`
Expected: BUILD SUCCESS（コンパイル成功。残存テスト = `ArchitectureTest` / `AppConfigTest`(残す場合) / `AppUrlBuilderTest` / `SystemClockAdapterTest` / `RcbLogFormatterTest` / `RcbMessageResolverTest` が緑）。この時点でカバレッジ hard gate は評価しない（`-Pfast test` は unit のみで jacoco:check を伴わない）。

- [ ] **Step 12: 残存 leave/認証参照の grep 確認**

Run: `grep -rniE "leave|login\.xhtml|dev-users|manager-layer|DEV_LOGIN|UserInfoContext|AuthenticationFilter" src/main src/test --include=*.java --include=*.xhtml --include=*.properties --include=*.xml | grep -v "userinfo/context\|userinfo/dto"`
Expected: 0 件（`userinfo` パッケージ本体＝社内ライブラリ準拠 IF のみ許容。それ以外に leave/認証残存が無いこと）。

- [ ] **Step 13: 不要になった SnakeYAML 依存を削除**

`DevLoginAuthenticationAdapter` 削除で `dev-users.yml` 読み込み用の SnakeYAML が不要になる。他用途が無いことを確認して `pom.xml` の該当 `<dependency>` を削除する。

Run: `grep -ni "snakeyaml\|org.yaml" pom.xml src`
Expected: 確認後、`pom.xml` の SnakeYAML `<dependency>` ブロック（コメント「YAML（dev-users.yml の読み込み用）」付近）のみ削除。他ソースに `org.yaml` 参照が無いこと。

Run: `scripts/with-env.sh ./mvnw -Pfast test`
Expected: BUILD SUCCESS（依存削除後もコンパイル・残存テストが green）。

- [ ] **Step 14: コミット**

```bash
git add -A
git commit -m "refactor: 休暇申請サンプルと開発用ログイン認証を撤去し骨格化

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase 2: ドメイン層（中心）

### Task 2: `TaskStatus` enum

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/domain/model/TaskStatus.java`
- Test: `src/test/java/jp/mufg/it/rcb/domain/model/TaskStatusTest.java`

**Interfaces:**

- Produces: `enum TaskStatus { TODO, DONE }` に `boolean canComplete()`（TODO のとき true）。

- [ ] **Step 1: 失敗するテストを書く**

```java
package jp.mufg.it.rcb.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link TaskStatus} の単体テスト. */
class TaskStatusTest {

  /* default */ TaskStatusTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** TODO は完了可能。 */
  @Test
  void todoCanComplete() {
    assertThat(TaskStatus.TODO.canComplete()).isTrue();
  }

  /** DONE は完了不可（再完了不可）。 */
  @Test
  void doneCannotComplete() {
    assertThat(TaskStatus.DONE.canComplete()).isFalse();
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=TaskStatusTest`
Expected: FAIL（`TaskStatus` が存在せずコンパイルエラー）。

- [ ] **Step 3: 最小実装**

```java
package jp.mufg.it.rcb.domain.model;

/** タスクの状態. */
public enum TaskStatus {
  /** 未完了. */
  TODO,
  /** 完了. */
  DONE;

  /**
   * 完了操作が可能か判定する. 完了は未完了（TODO）からのみ可能。
   *
   * @return 完了可能なら true
   */
  public boolean canComplete() {
    return this == TODO;
  }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=TaskStatusTest`
Expected: PASS（2 テスト成功）。

- [ ] **Step 5: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/domain/model/TaskStatus.java \
        src/test/java/jp/mufg/it/rcb/domain/model/TaskStatusTest.java
git commit -m "feat: TaskStatus enum（TODO/DONE と完了可否判定）を追加"
```

### Task 3: `Task` 集約

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/domain/model/Task.java`
- Test: `src/test/java/jp/mufg/it/rcb/domain/model/TaskTest.java`

**Interfaces:**

- Consumes: `TaskStatus`（Task 2）。
- Produces:
  - `Task.create(String title, Instant createdAt)` → status=TODO / completedAt=null。
  - `Task.reconstruct(Long id, String title, TaskStatus status, Instant createdAt, Instant completedAt)`（Mapper・テスト用）。
  - `void complete(Instant completedAt)` → TODO のときのみ DONE へ遷移。DONE で呼ぶと `IllegalStateException`。
  - Getter: `getId()`（Long）/ `getTitle()` / `getStatus()` / `getCreatedAt()`（Instant）/ `getCompletedAt()`（Instant, nullable）。

- [ ] **Step 1: 失敗するテストを書く**

```java
package jp.mufg.it.rcb.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** {@link Task} の単体テスト. */
class TaskTest {

  /** 生成基準時刻. */
  private static final Instant NOW = Instant.parse("2026-07-03T09:00:00Z");

  /* default */ TaskTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** create() は TODO / completedAt=null で生成する。 */
  @Test
  void createStartsAsTodo() {
    final Task task = Task.create("買い物", NOW);
    assertThat(task.getId()).isNull();
    assertThat(task.getTitle()).isEqualTo("買い物");
    assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(task.getCreatedAt()).isEqualTo(NOW);
    assertThat(task.getCompletedAt()).isNull();
  }

  /** complete() は TODO→DONE に遷移し completedAt を設定する。 */
  @Test
  void completeTransitionsToDone() {
    final Task task = Task.create("買い物", NOW);
    final Instant completedAt = Instant.parse("2026-07-03T10:00:00Z");
    task.complete(completedAt);
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(completedAt);
  }

  /** 既に DONE のタスクを complete() すると IllegalStateException（設計バグ防御）。 */
  @Test
  void completeOnDoneThrows() {
    final Task task = Task.create("買い物", NOW);
    task.complete(Instant.parse("2026-07-03T10:00:00Z"));
    assertThatThrownBy(() -> task.complete(Instant.parse("2026-07-03T11:00:00Z")))
        .isInstanceOf(IllegalStateException.class);
  }

  /** title が空文字だと生成できない（値オブジェクト境界での不変条件）。 */
  @Test
  void createRejectsBlankTitle() {
    assertThatThrownBy(() -> Task.create("  ", NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  /** reconstruct() は永続層の値をそのまま復元する。 */
  @Test
  void reconstructRestoresState() {
    final Instant completedAt = Instant.parse("2026-07-03T10:00:00Z");
    final Task task = Task.reconstruct(5L, "会議", TaskStatus.DONE, NOW, completedAt);
    assertThat(task.getId()).isEqualTo(5L);
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(completedAt);
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=TaskTest`
Expected: FAIL（`Task` が存在せずコンパイルエラー）。

- [ ] **Step 3: 最小実装**

```java
package jp.mufg.it.rcb.domain.model;

import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/** タスクエンティティ（TODO → DONE の状態遷移を担う集約ルート）. */
@Getter
public final class Task {

  /** 永続化 ID（新規作成時は null）. */
  private final Long id;

  /** タイトル（必須・非空白）. */
  private final String title;

  /** 現在の状態. */
  private TaskStatus status;

  /** 作成日時. */
  private final Instant createdAt;

  /** 完了日時（未完了時は null）. */
  private Instant completedAt;

  /** フィールドを一括設定する内部コンストラクタ（id / completedAt 以外は null 不可）. */
  private Task(
      final Long id,
      final String title,
      final TaskStatus status,
      final Instant createdAt,
      final Instant completedAt) {
    this.id = id;
    this.title = requireNonBlank(title);
    this.status = Objects.requireNonNull(status, "status");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.completedAt = completedAt;
  }

  /**
   * 新規タスクを生成する（status は TODO 固定、completedAt は null）.
   *
   * @param title タイトル（必須・非空白）
   * @param createdAt 作成日時（null 不可）
   * @return TODO 状態の新規 Task
   */
  public static Task create(final String title, final Instant createdAt) {
    return new Task(null, title, TaskStatus.TODO, createdAt, null);
  }

  /**
   * 永続化層から復元するためのファクトリ（Mapper・テスト用）.
   *
   * @param id 永続化 ID
   * @param title タイトル
   * @param status 状態
   * @param createdAt 作成日時
   * @param completedAt 完了日時（未完了時 null）
   * @return 復元された Task
   */
  public static Task reconstruct(
      final Long id,
      final String title,
      final TaskStatus status,
      final Instant createdAt,
      final Instant completedAt) {
    return new Task(id, title, status, createdAt, completedAt);
  }

  /**
   * タスクを完了する.
   *
   * <p>TODO からのみ遷移可能。既に DONE の場合はドメイン不変条件違反として {@link IllegalStateException} を投げる
   * （UseCase 側で事前に状態を再評価し業務エラーへ翻訳する前提の、到達したら設計バグとなる防御）。
   *
   * @param completedAt 完了日時（null 不可）
   * @throws IllegalStateException 現在の状態が TODO でない場合
   */
  public void complete(final Instant completedAt) {
    if (!this.status.canComplete()) {
      throw new IllegalStateException("Only TODO tasks can be completed; current=" + this.status);
    }
    this.status = TaskStatus.DONE;
    this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
  }

  /** タイトルの非空白を検証する. */
  private static String requireNonBlank(final String title) {
    Objects.requireNonNull(title, "title");
    if (title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    return title;
  }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=TaskTest`
Expected: PASS（5 テスト成功）。

- [ ] **Step 5: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/domain/model/Task.java \
        src/test/java/jp/mufg/it/rcb/domain/model/TaskTest.java
git commit -m "feat: Task 集約（生成・完了・不変条件）を追加"
```

---

## Phase 3: アプリケーション層

### Task 4: port（out.TaskRepositoryPort / in の UseCase・DTO・Command）

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/application/port/out/TaskRepositoryPort.java`
- Create: `src/main/java/jp/mufg/it/rcb/application/port/in/TaskSummary.java`
- Create: `src/main/java/jp/mufg/it/rcb/application/port/in/ListTasksUseCase.java`
- Create: `src/main/java/jp/mufg/it/rcb/application/port/in/CreateTaskCommand.java`
- Create: `src/main/java/jp/mufg/it/rcb/application/port/in/CreateTaskUseCase.java`
- Create: `src/main/java/jp/mufg/it/rcb/application/port/in/CompleteTaskUseCase.java`
- Test: `src/test/java/jp/mufg/it/rcb/application/port/in/CreateTaskCommandTest.java`

**Interfaces:**

- Consumes: `Task`（Task 3）, `TaskStatus`（Task 2）, `ClockPort`（保持済）。
- Produces:
  - `TaskRepositoryPort`: `Task save(Task task)` / `Optional<Task> findById(long taskId)` / `List<Task> findAllOrderByCreatedAtDesc()`。
  - `TaskSummary`（Serializable, `@Builder`）: `Long id` / `String title` / `TaskStatus status` / `Instant createdAt` / `Instant completedAt`。
  - `ListTasksUseCase`: `List<TaskSummary> list()`。
  - `CreateTaskCommand`（`@Builder`）: `@NotBlank @Size(max = 100) String title`。
  - `CreateTaskUseCase`: `void create(@Valid CreateTaskCommand command)`。
  - `CompleteTaskUseCase`: `void complete(long taskId)`。

- [ ] **Step 1: 失敗するテスト（CreateTaskCommand の Bean Validation）を書く**

```java
package jp.mufg.it.rcb.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** {@link CreateTaskCommand} の Bean Validation 単体テスト. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreateTaskCommandTest {

  /** Validator ファクトリ. */
  private ValidatorFactory factory;

  /** 検証対象 Validator. */
  private Validator validator;

  /* default */ CreateTaskCommandTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** Validator を初期化する。 */
  @BeforeAll
  void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  /** ValidatorFactory を閉じる。 */
  @AfterAll
  void tearDown() {
    if (factory != null) {
      factory.close();
    }
  }

  /** 正常なタイトルは違反なし。 */
  @Test
  void validTitlePasses() {
    final CreateTaskCommand cmd = CreateTaskCommand.builder().title("買い物").build();
    assertThat(validator.validate(cmd)).isEmpty();
  }

  /** 空白タイトルは @NotBlank 違反。 */
  @Test
  void blankTitleFails() {
    final CreateTaskCommand cmd = CreateTaskCommand.builder().title("  ").build();
    assertThat(validator.validate(cmd)).isNotEmpty();
  }

  /** null タイトルは @NotBlank 違反。 */
  @Test
  void nullTitleFails() {
    final CreateTaskCommand cmd = CreateTaskCommand.builder().title(null).build();
    assertThat(validator.validate(cmd)).isNotEmpty();
  }

  /** 100 文字は許容、101 文字は @Size 違反。 */
  @Test
  void titleLengthBoundary() {
    final CreateTaskCommand ok = CreateTaskCommand.builder().title("あ".repeat(100)).build();
    final CreateTaskCommand ng = CreateTaskCommand.builder().title("あ".repeat(101)).build();
    assertThat(validator.validate(ok)).isEmpty();
    assertThat(validator.validate(ng)).isNotEmpty();
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=CreateTaskCommandTest`
Expected: FAIL（`CreateTaskCommand` 未存在でコンパイルエラー）。

- [ ] **Step 3: port クラス群を実装**

`TaskRepositoryPort.java`:

```java
package jp.mufg.it.rcb.application.port.out;

import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.domain.model.Task;

/** タスクの永続化を担う出力 port. */
public interface TaskRepositoryPort {

  /**
   * タスクを永続化する.
   *
   * @param task 永続化対象（新規時は ID 未採番、更新時は採番済み）
   * @return 採番済み（または更新済み）のタスク
   */
  Task save(Task task);

  /**
   * ID を指定してタスクを取得する.
   *
   * @param taskId タスク ID
   * @return 該当するタスク（存在しない場合は空）
   */
  Optional<Task> findById(long taskId);

  /**
   * 全タスクを作成日時の降順で取得する.
   *
   * @return タスクの一覧（新しい順）
   */
  List<Task> findAllOrderByCreatedAtDesc();
}
```

`TaskSummary.java`:

```java
package jp.mufg.it.rcb.application.port.in;

import java.io.Serializable;
import java.time.Instant;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * タスク一覧表示用 DTO.
 *
 * <p>{@code @ViewScoped} バッキングビーン（TaskListBean）のフィールドとして保持されるため Serializable。
 */
@Getter
@Builder
@AllArgsConstructor
public class TaskSummary implements Serializable {

  private static final long serialVersionUID = 1L;

  /** タスク ID. */
  private final Long id;

  /** タイトル. */
  private final String title;

  /** 状態. */
  private final TaskStatus status;

  /** 作成日時. */
  private final Instant createdAt;

  /** 完了日時（未完了時は null）. */
  private final Instant completedAt;
}
```

`ListTasksUseCase.java`:

```java
package jp.mufg.it.rcb.application.port.in;

import java.util.List;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加も想定するため lambda 化を意図しない。
/** タスク一覧取得ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ListTasksUseCase {

  /**
   * 全タスクを作成日時の降順で取得する.
   *
   * @return 一覧表示用 DTO の一覧
   */
  List<TaskSummary> list();
}
```

`CreateTaskCommand.java`:

```java
package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * タスク新規作成ユースケースの入力コマンド.
 *
 * <p>Bean Validation でタイトルの必須・最大長（DB の title VARCHAR(100) と一致）を検証する。
 */
@Getter
@Builder
@AllArgsConstructor
public class CreateTaskCommand {

  /** タイトル（必須、100 字以内）. */
  @NotBlank
  @Size(max = 100)
  private final String title;
}
```

`CreateTaskUseCase.java`:

```java
package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.Valid;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加も想定するため lambda 化を意図しない。
/** タスク新規作成ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface CreateTaskUseCase {

  /**
   * タスクを新規作成して永続化する.
   *
   * @param command 入力コマンド
   */
  void create(@Valid CreateTaskCommand command);
}
```

`CompleteTaskUseCase.java`:

```java
package jp.mufg.it.rcb.application.port.in;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加も想定するため lambda 化を意図しない。
/** タスク完了ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface CompleteTaskUseCase {

  /**
   * タスクを完了する.
   *
   * @param taskId 対象タスク ID
   */
  void complete(long taskId);
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=CreateTaskCommandTest`
Expected: PASS（5 テスト成功）。

- [ ] **Step 5: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/application/port \
        src/test/java/jp/mufg/it/rcb/application/port/in/CreateTaskCommandTest.java
git commit -m "feat: タスクの port（TaskRepositoryPort / UseCase / Command / Summary）を追加"
```

### Task 5: `CreateTaskService`

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/application/service/CreateTaskService.java`
- Test: `src/test/java/jp/mufg/it/rcb/application/service/CreateTaskServiceTest.java`

**Interfaces:**

- Consumes: `CreateTaskUseCase` / `CreateTaskCommand`（Task 4）, `TaskRepositoryPort`（Task 4）, `ClockPort`, `Task`（Task 3）。
- Produces: `CreateTaskService implements CreateTaskUseCase`（`@ApplicationScoped @Transactional`）。作成成功で `RCB00001-I` を INFO ログ。

- [ ] **Step 1: 失敗するテストを書く**

```java
package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.CreateTaskCommand;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link CreateTaskService} の単体テスト. */
@ExtendWith(MockitoExtension.class)
class CreateTaskServiceTest {

  /** 永続化 port のモック. */
  @Mock private TaskRepositoryPort repository;

  /** Logger のモック（@InjectLogger は単体テストでは注入されないため）. */
  @Mock private Logger sysLogger;

  /** 固定時刻クロック. */
  private final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-07-03T09:00:00Z"));

  /* default */ CreateTaskServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** sysLogger を差し込んだ Service を構築する. */
  private CreateTaskService newService() {
    final CreateTaskService svc = new CreateTaskService(repository, clock);
    svc.sysLogger = sysLogger;
    return svc;
  }

  /** create() は TODO のタスクを作成日時付きで保存する。 */
  @Test
  void createsTodoTaskWithClockTime() {
    when(repository.save(any())).thenAnswer(inv -> Task.reconstruct(1L, "買い物",
        TaskStatus.TODO, Instant.parse("2026-07-03T09:00:00Z"), null));

    newService().create(CreateTaskCommand.builder().title("買い物").build());

    final ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
    verify(repository).save(captor.capture());
    final Task saved = captor.getValue();
    assertThat(saved.getTitle()).isEqualTo("買い物");
    assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
    assertThat(saved.getId()).isNull();
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=CreateTaskServiceTest`
Expected: FAIL（`CreateTaskService` 未存在）。

- [ ] **Step 3: 最小実装**

```java
package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.CreateTaskCommand;
import jp.mufg.it.rcb.application.port.in.CreateTaskUseCase;
import jp.mufg.it.rcb.application.port.out.ClockPort;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;

/** タスク新規作成ユースケースの実装（成功時に業務イベント INFO ログを emit）. */
@ApplicationScoped
public class CreateTaskService implements CreateTaskUseCase {

  /** 永続化 port. */
  private final TaskRepositoryPort repository;

  /** 時刻取得 port. */
  private final ClockPort clock;

  /**
   * 業務イベント用 Logger.
   *
   * <p>パッケージ private 可視性は同パッケージのテストから直接 mock を差し込めるよう意図的に採用している。
   */
  @Inject
  @InjectLogger(LoggerType.SYSTEM)
  /* default */ Logger sysLogger;

  /**
   * 依存を注入する.
   *
   * @param repository 永続化 port
   * @param clock 時刻取得 port
   */
  @Inject
  public CreateTaskService(final TaskRepositoryPort repository, final ClockPort clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void create(@Valid final CreateTaskCommand cmd) {
    final Task task = Task.create(cmd.getTitle(), clock.now());
    final Task saved = repository.save(task);
    // PMD GuardLogStatement: Object[] パラメータ配列生成を回避するため Level ガードを挟む.
    if (sysLogger.isLoggable(Level.INFO)) {
      sysLogger.log(Level.INFO, "RCB00001-I", new Object[] {saved.getId()});
    }
  }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=CreateTaskServiceTest`
Expected: PASS。

- [ ] **Step 5: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/application/service/CreateTaskService.java \
        src/test/java/jp/mufg/it/rcb/application/service/CreateTaskServiceTest.java
git commit -m "feat: CreateTaskService（タスク作成 + INFO ログ）を追加"
```

---

### Task 6: `CompleteTaskService`（業務エラー回復可 / 回復不可の 2 経路）

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/application/service/CompleteTaskService.java`
- Test: `src/test/java/jp/mufg/it/rcb/application/service/CompleteTaskServiceTest.java`

**Interfaces:**

- Consumes: `CompleteTaskUseCase`（Task 4）, `TaskRepositoryPort`, `ClockPort`, `Task` / `TaskStatus`, `MSTBusinessException`, `MSTBusinessNonRecoverException`。
- Produces: `CompleteTaskService implements CompleteTaskUseCase`（`@ApplicationScoped @Transactional`）。
  - 対象不存在 → `MSTBusinessNonRecoverException`（回復不可・POST 偽装/データ不整合）。
  - 既に DONE → `MSTBusinessException`（回復可）。
  - 成功 → `Task.complete(clock.now())` + save + `RCB00002-I` INFO ログ。

**設計メモ:** `MSTBusinessNonRecoverException` のコンストラクタは `(String[] logMessageParams, String[] errorMessageParams, Throwable cause)`。`errorMessageParams[0]` が `default.properties` の `exceptionhandler.unknown-business.message={0}`（Task 13 で追加）に流れ、画面表示される。`MSTBusinessException` は `(String... errorMessageParams)` 可変長で、`exceptionhandler.invalid-operation.message={0}` に流れる。

- [ ] **Step 1: 失敗するテストを書く**

```java
package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.exception.inner.MSTBusinessNonRecoverException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link CompleteTaskService} の単体テスト. */
@ExtendWith(MockitoExtension.class)
class CompleteTaskServiceTest {

  /** 永続化 port のモック. */
  @Mock private TaskRepositoryPort repository;

  /** Logger のモック. */
  @Mock private Logger sysLogger;

  /** 固定時刻クロック（完了日時の検証用）. */
  private final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-07-03T10:00:00Z"));

  /* default */ CompleteTaskServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** TODO のタスク（id=1）を生成する. */
  private Task todoTask() {
    return Task.reconstruct(1L, "買い物", TaskStatus.TODO,
        Instant.parse("2026-07-03T09:00:00Z"), null);
  }

  /** sysLogger を差し込んだ Service を構築する. */
  private CompleteTaskService newService() {
    final CompleteTaskService svc = new CompleteTaskService(repository, clock);
    svc.sysLogger = sysLogger;
    return svc;
  }

  /** 正常系：TODO を DONE に遷移し完了日時を設定して保存する。 */
  @Test
  void completesTodoTask() {
    when(repository.findById(1L)).thenReturn(Optional.of(todoTask()));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    newService().complete(1L);

    final ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
    verify(repository).save(captor.capture());
    final Task saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(saved.getCompletedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
  }

  /** 異常系（回復可）：既に DONE のタスクは MSTBusinessException で拒否する。 */
  @Test
  void rejectsAlreadyDoneWithRecoverableError() {
    final Task done = todoTask();
    done.complete(Instant.parse("2026-07-03T09:30:00Z"));
    when(repository.findById(1L)).thenReturn(Optional.of(done));

    final CompleteTaskService svc = newService();
    assertThatThrownBy(() -> svc.complete(1L)).isInstanceOf(MSTBusinessException.class);
    verify(repository, never()).save(any());
  }

  /** 異常系（回復不可）：対象が存在しない場合は MSTBusinessNonRecoverException を投げる。 */
  @Test
  void throwsNonRecoverableWhenNotFound() {
    when(repository.findById(999L)).thenReturn(Optional.empty());

    final CompleteTaskService svc = newService();
    assertThatThrownBy(() -> svc.complete(999L))
        .isInstanceOf(MSTBusinessNonRecoverException.class);
    verify(repository, never()).save(any());
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=CompleteTaskServiceTest`
Expected: FAIL（`CompleteTaskService` 未存在）。

- [ ] **Step 3: 最小実装**

```java
package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.CompleteTaskUseCase;
import jp.mufg.it.rcb.application.port.out.ClockPort;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.exception.inner.MSTBusinessNonRecoverException;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;

/**
 * タスク完了ユースケースの実装.
 *
 * <p>対象不存在は「削除機能を持たない本サンプルでは通常起こり得ない想定外状態（POST 偽装/データ不整合）」として
 * 回復不可の {@link MSTBusinessNonRecoverException} を送出する。既に完了済みの再完了はユーザが取り消せる
 * 回復可の業務エラー {@link MSTBusinessException} として現画面に留置する。
 */
@ApplicationScoped
public class CompleteTaskService implements CompleteTaskUseCase {

  /** 永続化 port. */
  private final TaskRepositoryPort repository;

  /** 時刻取得 port. */
  private final ClockPort clock;

  /**
   * 業務イベント用 Logger.
   *
   * <p>パッケージ private 可視性は同パッケージのテストから直接 mock を差し込めるよう意図的に採用している。
   */
  @Inject
  @InjectLogger(LoggerType.SYSTEM)
  /* default */ Logger sysLogger;

  /**
   * 依存を注入する.
   *
   * @param repository 永続化 port
   * @param clock 時刻取得 port
   */
  @Inject
  public CompleteTaskService(final TaskRepositoryPort repository, final ClockPort clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void complete(final long taskId) {
    final Task task =
        repository
            .findById(taskId)
            .orElseThrow(
                () ->
                    new MSTBusinessNonRecoverException(
                        new String[] {"task not found: id=" + taskId},
                        new String[] {"指定されたタスクが見つかりません"},
                        null));

    if (task.getStatus() != TaskStatus.TODO) {
      throw new MSTBusinessException("既に完了したタスクです");
    }

    task.complete(clock.now());
    repository.save(task);
    // PMD GuardLogStatement: Object[] パラメータ配列生成を回避するため Level ガードを挟む.
    if (sysLogger.isLoggable(Level.INFO)) {
      sysLogger.log(Level.INFO, "RCB00002-I", new Object[] {task.getId()});
    }
  }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=CompleteTaskServiceTest`
Expected: PASS（3 テスト成功）。

- [ ] **Step 5: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/application/service/CompleteTaskService.java \
        src/test/java/jp/mufg/it/rcb/application/service/CompleteTaskServiceTest.java
git commit -m "feat: CompleteTaskService（回復可/回復不可の業務エラー2経路）を追加"
```

### Task 7: `ListTasksService`

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/application/service/ListTasksService.java`
- Test: `src/test/java/jp/mufg/it/rcb/application/service/ListTasksServiceTest.java`

**Interfaces:**

- Consumes: `ListTasksUseCase` / `TaskSummary`（Task 4）, `TaskRepositoryPort`, `Task`。
- Produces: `ListTasksService implements ListTasksUseCase`（`@ApplicationScoped @Transactional(SUPPORTS)`）。`Task` → `TaskSummary` 変換。

- [ ] **Step 1: 失敗するテストを書く**

```java
package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import jp.mufg.it.rcb.application.port.in.TaskSummary;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ListTasksService} の単体テスト. */
@ExtendWith(MockitoExtension.class)
class ListTasksServiceTest {

  /** 永続化 port のモック. */
  @Mock private TaskRepositoryPort repository;

  /* default */ ListTasksServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** repository の Task を TaskSummary に変換して返す。 */
  @Test
  void mapsTasksToSummaries() {
    final Task task = Task.reconstruct(1L, "買い物", TaskStatus.TODO,
        Instant.parse("2026-07-03T09:00:00Z"), null);
    when(repository.findAllOrderByCreatedAtDesc()).thenReturn(List.of(task));

    final List<TaskSummary> result = new ListTasksService(repository).list();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(1L);
    assertThat(result.get(0).getTitle()).isEqualTo("買い物");
    assertThat(result.get(0).getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(result.get(0).getCreatedAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
    assertThat(result.get(0).getCompletedAt()).isNull();
  }

  /** タスクが無い場合は空リストを返す。 */
  @Test
  void returnsEmptyWhenNoTasks() {
    when(repository.findAllOrderByCreatedAtDesc()).thenReturn(List.of());
    assertThat(new ListTasksService(repository).list()).isEmpty();
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=ListTasksServiceTest`
Expected: FAIL（`ListTasksService` 未存在）。

- [ ] **Step 3: 最小実装**

```java
package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import jp.mufg.it.rcb.application.port.in.ListTasksUseCase;
import jp.mufg.it.rcb.application.port.in.TaskSummary;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;

/** タスク一覧ユースケースの実装（読み取り専用で全件を作成日時降順に取得）. */
@ApplicationScoped
public class ListTasksService implements ListTasksUseCase {

  /** 永続化 port. */
  private final TaskRepositoryPort repository;

  /**
   * 依存を注入する.
   *
   * @param repository 永続化 port
   */
  @Inject
  public ListTasksService(final TaskRepositoryPort repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public List<TaskSummary> list() {
    return repository.findAllOrderByCreatedAtDesc().stream().map(this::toSummary).toList();
  }

  /**
   * Task を表示用 DTO に変換する.
   *
   * @param task 変換対象のタスク
   * @return 表示用 DTO
   */
  private TaskSummary toSummary(final Task task) {
    return TaskSummary.builder()
        .id(task.getId())
        .title(task.getTitle())
        .status(task.getStatus())
        .createdAt(task.getCreatedAt())
        .completedAt(task.getCompletedAt())
        .build();
  }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=ListTasksServiceTest`
Expected: PASS（2 テスト成功）。

- [ ] **Step 5: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/application/service/ListTasksService.java \
        src/test/java/jp/mufg/it/rcb/application/service/ListTasksServiceTest.java
git commit -m "feat: ListTasksService（一覧取得 + Summary 変換）を追加"
```

---

## Phase 4: 永続化アダプタ層

### Task 8: `TaskEntity` + `TaskMapper` + マイグレーション SQL

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/adapter/out/persistence/TaskEntity.java`
- Create: `src/main/java/jp/mufg/it/rcb/adapter/out/persistence/TaskMapper.java`
- Create: `src/main/resources/db/migration/V1__init.sql`
- Create: `src/main/resources/db/migration/R__dev_seed.sql`
- Test: `src/test/java/jp/mufg/it/rcb/adapter/out/persistence/TaskMapperTest.java`

**Interfaces:**

- Consumes: `Task` / `TaskStatus`。
- Produces:
  - `TaskEntity`（`@Entity @Table(name="task")`, Lombok `@Getter/@Setter/@NoArgsConstructor`）。
  - `TaskMapper`（`@ApplicationScoped`）: `TaskEntity toEntity(Task)` / `void applyToEntity(Task src, TaskEntity dst)` / `Task toDomain(TaskEntity)`。

- [ ] **Step 1: 失敗するテスト（Mapper 双方向変換）を書く**

```java
package jp.mufg.it.rcb.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

/** {@link TaskMapper} の単体テスト. */
class TaskMapperTest {

  /** テスト対象. */
  private final TaskMapper mapper = new TaskMapper();

  /* default */ TaskMapperTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** ドメイン→Entity 変換で全項目が転写される（未完了 = completedAt null）。 */
  @Test
  void toEntityCopiesAllFields() {
    final Task task = Task.reconstruct(1L, "買い物", TaskStatus.TODO,
        Instant.parse("2026-07-03T09:00:00Z"), null);
    final TaskEntity entity = mapper.toEntity(task);
    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getTitle()).isEqualTo("買い物");
    assertThat(entity.getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(entity.getCreatedAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
    assertThat(entity.getCompletedAt()).isNull();
  }

  /** Entity→ドメイン復元で完了日時付き（DONE）が復元される。 */
  @Test
  void toDomainRestoresDoneTask() {
    final TaskEntity entity = new TaskEntity();
    entity.setId(2L);
    entity.setTitle("会議");
    entity.setStatus(TaskStatus.DONE);
    entity.setCreatedAt(Instant.parse("2026-07-03T09:00:00Z"));
    entity.setCompletedAt(Instant.parse("2026-07-03T10:00:00Z"));

    final Task task = mapper.toDomain(entity);
    assertThat(task.getId()).isEqualTo(2L);
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=TaskMapperTest`
Expected: FAIL（`TaskEntity` / `TaskMapper` 未存在）。

- [ ] **Step 3: `TaskEntity` を実装**

```java
package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** タスクテーブル（task）の JPA Entity. */
@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
public class TaskEntity {

  /** 主キー（DB IDENTITY 採番）. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** タイトル. */
  @Column(name = "title", nullable = false, length = 100)
  private String title;

  /** 状態. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 4)
  private TaskStatus status;

  /** 作成日時. */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 完了日時（未完了時 null）. */
  @Column(name = "completed_at")
  private Instant completedAt;
}
```

- [ ] **Step 4: `TaskMapper` を実装**

```java
package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jp.mufg.it.rcb.domain.model.Task;

/** ドメイン Task と JPA Entity TaskEntity を相互変換するマッパー. */
@ApplicationScoped
public class TaskMapper {

  /** デフォルトコンストラクタ（CDI 仕様により公開する）. */
  public TaskMapper() {
    // CDI が ApplicationScoped Bean として生成するため初期化処理は不要。
  }

  /**
   * ドメインから新規 Entity を生成する.
   *
   * @param domain 変換元ドメイン（null 不可）
   * @return 新規生成された {@link TaskEntity}
   */
  public TaskEntity toEntity(final Task domain) {
    final TaskEntity entity = new TaskEntity();
    applyToEntity(domain, entity);
    return entity;
  }

  /**
   * 既存 Entity にドメインの値を反映する（in-place 更新）.
   *
   * @param src 反映元ドメイン（null 不可）
   * @param dst 反映先 Entity（null 不可）
   */
  public void applyToEntity(final Task src, final TaskEntity dst) {
    dst.setId(src.getId());
    dst.setTitle(src.getTitle());
    dst.setStatus(src.getStatus());
    dst.setCreatedAt(src.getCreatedAt());
    dst.setCompletedAt(src.getCompletedAt());
  }

  /**
   * Entity をドメインへ復元する.
   *
   * @param entity 変換元 Entity（null 不可）
   * @return 復元された {@link Task}
   */
  public Task toDomain(final TaskEntity entity) {
    return Task.reconstruct(
        entity.getId(),
        entity.getTitle(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getCompletedAt());
  }
}
```

- [ ] **Step 5: マイグレーション SQL を作成**

`V1__init.sql`:

```sql
-- PostgreSQL / H2 (MODE=PostgreSQL) 両対応の DDL。
-- BIGSERIAL は PG 固有のため、SQL 標準の GENERATED BY DEFAULT AS IDENTITY を採用
-- （PG 10+ / H2 2.x ともにサポート）。E2E は WildFly + H2 ファイル DB で本ファイルを
-- Flyway maven plugin で migrate する。IT (JpaTestSupport.bootstrapH2) も同じファイルを
-- classpath から直接 execute するため、テスト用に別 V1 を持たない。
CREATE TABLE task (
  id            BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  title         VARCHAR(100) NOT NULL,
  status        VARCHAR(4)   NOT NULL,
  created_at    TIMESTAMP    NOT NULL,
  completed_at  TIMESTAMP,
  CONSTRAINT chk_task_status CHECK (status IN ('TODO','DONE')),
  CONSTRAINT chk_task_completed CHECK (
    (status = 'TODO' AND completed_at IS NULL) OR
    (status = 'DONE' AND completed_at IS NOT NULL)
  )
);

CREATE INDEX idx_task_created_at ON task (created_at);
```

`R__dev_seed.sql`:

```sql
-- 開発・E2E 用のシードデータ。
-- Flyway 標準ロケーション (db/migration) 配下の Repeatable migration として配置するため、
-- ローカル PG / E2E H2 / CI のいずれでも `flyway:migrate` 一発で投入される。
-- DELETE FROM で冪等性を確保しているため、Flyway が R__* の content hash 変化を検知して
-- 再実行しても整合性を保つ。
-- IT (JpaTestSupport.bootstrapH2) は V1 のみを classpath から直接 execute するため、
-- 本 seed は IT には影響しない。
-- 一覧・完了シナリオの起点として TODO を数件、表示確認用に DONE を 1 件投入する。

DELETE FROM task;

INSERT INTO task (title, status, created_at, completed_at)
VALUES
  ('要件定義レビューの準備', 'TODO', '2026-07-01T09:00:00', NULL),
  ('サンプルアプリの動作確認', 'TODO', '2026-07-02T10:30:00', NULL),
  ('キックオフ議事録の作成', 'DONE', '2026-06-30T14:00:00', '2026-06-30T15:00:00');
```

- [ ] **Step 6: テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=TaskMapperTest`
Expected: PASS（2 テスト成功）。

- [ ] **Step 7: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/adapter/out/persistence/TaskEntity.java \
        src/main/java/jp/mufg/it/rcb/adapter/out/persistence/TaskMapper.java \
        src/main/resources/db/migration/V1__init.sql \
        src/main/resources/db/migration/R__dev_seed.sql \
        src/test/java/jp/mufg/it/rcb/adapter/out/persistence/TaskMapperTest.java
git commit -m "feat: TaskEntity / TaskMapper とマイグレーション SQL を追加"
```

### Task 9: `TaskRepositoryAdapter`（H2 結合テスト）

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/adapter/out/persistence/TaskRepositoryAdapter.java`
- Test: `src/test/java/jp/mufg/it/rcb/adapter/out/persistence/TaskRepositoryAdapterIT.java`

**Interfaces:**

- Consumes: `TaskRepositoryPort`, `TaskMapper`, `TaskEntity`, `Task`。
- Produces: `TaskRepositoryAdapter implements TaskRepositoryPort`（`@ApplicationScoped`, `@PersistenceContext(unitName="appPU") EntityManager em`）。

- [ ] **Step 1: 失敗するテストを書く**

```java
package jp.mufg.it.rcb.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import jp.mufg.it.rcb.shared.test.JpaTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

// 抑制理由:
//  - checkstyle:AbbreviationAsWordInName: クラス名末尾の "IT" は maven-failsafe が結合テストを
//    識別する標準命名規約であり、Google Java Style の連続大文字規則と構造的に衝突するため本クラス限定で抑制する。
/**
 * {@link TaskRepositoryAdapter} の結合テスト（H2 + 本物 Mapper）.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class TaskRepositoryAdapterIT {

  /** H2 bootstrap で生成する EntityManagerFactory. */
  private EntityManagerFactory emf;

  /** 各テストごとの EntityManager. */
  private EntityManager em;

  /** テスト対象（本物の Adapter）. */
  private TaskRepositoryAdapter adapter;

  /* default */ TaskRepositoryAdapterIT() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** H2 にスキーマを作成する。 */
  @BeforeAll
  void setUpAll() throws IOException, SQLException {
    emf =
        JpaTestSupport.bootstrapH2(
            "appTestPU",
            "jdbc:h2:mem:task-repo-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__init.sql");
  }

  /** EntityManagerFactory を閉じる。 */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** 各テスト前に空テーブルの Adapter を組み立てる。 */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    JpaTestSupport.runInTx(em, () -> em.createQuery("DELETE FROM TaskEntity").executeUpdate());
    adapter = JpaTestSupport.injectEntityManager(new TaskRepositoryAdapter(new TaskMapper()), em);
  }

  /** EntityManager を閉じる。 */
  @AfterEach
  void tearDown() {
    if (em != null && em.isOpen()) {
      em.close();
    }
  }

  /** save() で採番され、findById() で取得できる。 */
  @Test
  void savesAndFindsById() {
    final Task[] saved = new Task[1];
    JpaTestSupport.runInTx(
        em,
        () ->
            saved[0] =
                adapter.save(Task.create("買い物", Instant.parse("2026-07-03T09:00:00Z"))));
    assertThat(saved[0].getId()).isNotNull();

    em.clear();
    final Optional<Task> found = adapter.findById(saved[0].getId());
    assertThat(found).isPresent();
    assertThat(found.get().getTitle()).isEqualTo("買い物");
    assertThat(found.get().getStatus()).isEqualTo(TaskStatus.TODO);
  }

  /** findById() は存在しない ID で空を返す。 */
  @Test
  void findByIdReturnsEmptyWhenMissing() {
    assertThat(adapter.findById(999L)).isEmpty();
  }

  /** findAllOrderByCreatedAtDesc() は作成日時の降順で返す。 */
  @Test
  void findsAllOrderedByCreatedAtDesc() {
    JpaTestSupport.runInTx(
        em,
        () -> {
          adapter.save(Task.create("古い", Instant.parse("2026-07-01T09:00:00Z")));
          adapter.save(Task.create("新しい", Instant.parse("2026-07-03T09:00:00Z")));
        });
    em.clear();

    final List<Task> all = adapter.findAllOrderByCreatedAtDesc();
    assertThat(all).extracting(Task::getTitle).containsExactly("新しい", "古い");
  }

  /** save() の更新経路：既存タスクを完了状態で保存し直せる。 */
  @Test
  void updatesExistingTask() {
    final Task[] saved = new Task[1];
    JpaTestSupport.runInTx(
        em, () -> saved[0] = adapter.save(Task.create("会議", Instant.parse("2026-07-03T09:00:00Z"))));
    em.clear();

    final Task loaded = adapter.findById(saved[0].getId()).orElseThrow();
    loaded.complete(Instant.parse("2026-07-03T10:00:00Z"));
    JpaTestSupport.runInTx(em, () -> adapter.save(loaded));
    em.clear();

    final Task reloaded = adapter.findById(saved[0].getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(reloaded.getCompletedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast test -Dtest=TaskRepositoryAdapterIT`
Expected: FAIL（`TaskRepositoryAdapter` 未存在。IT は failsafe だが `-Dtest` + surefire でもコンパイル失敗を確認できる）。

- [ ] **Step 3: 最小実装**

```java
package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;

/** {@link TaskRepositoryPort} の JPA 実装（appPU をインジェクション）. */
@ApplicationScoped
public class TaskRepositoryAdapter implements TaskRepositoryPort {

  /** JPA の永続化コンテキスト（appPU）. テストから差し替え可能とするため package-private とする. */
  /* default */ @PersistenceContext(unitName = "appPU")
  EntityManager em;

  /** ドメインと Entity を相互変換するマッパー. */
  private final TaskMapper mapper;

  /**
   * CDI コンストラクタインジェクション.
   *
   * @param mapper ドメインと Entity を相互変換するマッパー
   */
  @Inject
  public TaskRepositoryAdapter(final TaskMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Task save(final Task task) {
    if (task.getId() == null) {
      final TaskEntity entity = mapper.toEntity(task);
      em.persist(entity);
      return mapper.toDomain(entity);
    }
    final TaskEntity existing = em.find(TaskEntity.class, task.getId());
    if (existing == null) {
      throw new IllegalStateException("Entity not found for id=" + task.getId());
    }
    mapper.applyToEntity(task, existing);
    return mapper.toDomain(existing);
  }

  @Override
  public Optional<Task> findById(final long taskId) {
    return Optional.ofNullable(em.find(TaskEntity.class, taskId)).map(mapper::toDomain);
  }

  @Override
  public List<Task> findAllOrderByCreatedAtDesc() {
    final TypedQuery<TaskEntity> query =
        em.createQuery(
            "SELECT e FROM TaskEntity e ORDER BY e.createdAt DESC", TaskEntity.class);
    return query.getResultList().stream().map(mapper::toDomain).toList();
  }
}
```

- [ ] **Step 4: 結合テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast verify -Dit.test=TaskRepositoryAdapterIT -Dtest=TaskMapperTest`
Expected: PASS（IT 4 テスト成功。failsafe が `*IT` を実行）。

- [ ] **Step 5: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/adapter/out/persistence/TaskRepositoryAdapter.java \
        src/test/java/jp/mufg/it/rcb/adapter/out/persistence/TaskRepositoryAdapterIT.java
git commit -m "feat: TaskRepositoryAdapter（JPA 実装）と H2 結合テストを追加"
```

---

## Phase 5: Web 層（バッキングビーン + xhtml）

### Task 10: `TaskFormBean` + `new.xhtml`（結合テスト）

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/adapter/in/web/TaskFormBean.java`
- Create: `src/main/webapp/tasks/new.xhtml`
- Test: `src/test/java/jp/mufg/it/rcb/adapter/in/web/TaskFormBeanIT.java`

**Interfaces:**

- Consumes: `CreateTaskUseCase` / `CreateTaskCommand`, 検証用に `TaskRepositoryAdapter` / `TaskMapper` / `CreateTaskService` / `FixedClockStub`。
- Produces: `TaskFormBean`（`@Named @ViewScoped`）: `String title`（getter/setter）, `String create()` → 成功で `"/tasks/list.xhtml?faces-redirect=true"`。

- [ ] **Step 1: 失敗するテストを書く**

```java
package jp.mufg.it.rcb.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.faces.context.FacesContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import jp.mufg.it.rcb.adapter.out.persistence.TaskMapper;
import jp.mufg.it.rcb.adapter.out.persistence.TaskRepositoryAdapter;
import jp.mufg.it.rcb.application.service.CreateTaskService;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import jp.mufg.it.rcb.shared.test.JpaTestSupport;
import jp.mufg.it.rcb.shared.test.ReflectionTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

// 抑制理由:
//  - checkstyle:AbbreviationAsWordInName: 末尾 "IT" は maven-failsafe の結合テスト命名規約。
/**
 * {@link TaskFormBean} の結合テスト（本物 Service / Adapter / H2 まで通す）.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class TaskFormBeanIT {

  /** H2 bootstrap で生成する EntityManagerFactory. */
  private EntityManagerFactory emf;

  /** 各テストごとの EntityManager. */
  private EntityManager em;

  /** テスト対象. */
  private TaskFormBean bean;

  /** DB 検証用の本物 Adapter（同一 EM 共有）. */
  private TaskRepositoryAdapter verifyAdapter;

  /** FacesContext static mock. */
  private MockedStatic<FacesContext> facesCtxMock;

  /* default */ TaskFormBeanIT() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** H2 にスキーマを作成する。 */
  @BeforeAll
  void setUpAll() throws IOException, SQLException {
    emf =
        JpaTestSupport.bootstrapH2(
            "appTestPU",
            "jdbc:h2:mem:task-form-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__init.sql");
  }

  /** EntityManagerFactory を閉じる。 */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** Bean → 本物 Service → 本物 Adapter → H2 を組み立てる。 */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    JpaTestSupport.runInTx(em, () -> em.createQuery("DELETE FROM TaskEntity").executeUpdate());

    verifyAdapter =
        JpaTestSupport.injectEntityManager(new TaskRepositoryAdapter(new TaskMapper()), em);
    final CreateTaskService service =
        new CreateTaskService(verifyAdapter, new FixedClockStub(Instant.parse("2026-07-03T09:00:00Z")));
    ReflectionTestSupport.injectField(service, "sysLogger", Logger.getLogger("test"));

    bean = new TaskFormBean();
    bean.createTask = service;

    facesCtxMock = Mockito.mockStatic(FacesContext.class);
    facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(Mockito.mock(FacesContext.class));
  }

  /** static mock と EM を解放する。 */
  @AfterEach
  void tearDown() {
    if (facesCtxMock != null) {
      facesCtxMock.close();
    }
    if (em != null && em.isOpen()) {
      em.close();
    }
  }

  /** create() で H2 に TODO タスクが作成され、一覧画面 outcome が返る。 */
  @Test
  void createPersistsTodoTask() {
    bean.setTitle("買い物");

    final String[] outcome = new String[1];
    JpaTestSupport.runInTx(em, () -> outcome[0] = bean.create());

    assertThat(outcome[0]).isEqualTo("/tasks/list.xhtml?faces-redirect=true");

    em.clear();
    final List<Task> persisted = verifyAdapter.findAllOrderByCreatedAtDesc();
    assertThat(persisted).hasSize(1);
    assertThat(persisted.get(0).getTitle()).isEqualTo("買い物");
    assertThat(persisted.get(0).getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(persisted.get(0).getCreatedAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast verify -Dit.test=TaskFormBeanIT`
Expected: FAIL（`TaskFormBean` 未存在）。

- [ ] **Step 3: `TaskFormBean` を実装**

```java
package jp.mufg.it.rcb.adapter.in.web;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import jp.mufg.it.rcb.application.port.in.CreateTaskCommand;
import jp.mufg.it.rcb.application.port.in.CreateTaskUseCase;
import lombok.Getter;
import lombok.Setter;

/**
 * タスク新規作成フォーム画面の Backing Bean.
 *
 * <p>タイトルを入力させ {@link CreateTaskUseCase} に委譲する。業務エラー・バリデーション違反は
 * ExceptionFacesResponseHandler / JSF が現画面に FacesMessage を表示するため本 Bean では catch しない。
 * 成功時は {@code /tasks/list.xhtml} に redirect する。
 */
@Named
@ViewScoped
public class TaskFormBean implements Serializable {

  private static final long serialVersionUID = 1L;

  /** タスク作成ユースケース port. */
  @Inject /* default */ CreateTaskUseCase createTask;

  /** タイトル入力値. */
  @Getter @Setter private String title;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public TaskFormBean() {
    // CDI Bean のため初期化処理は不要。
  }

  /**
   * 入力値で {@link CreateTaskUseCase} を呼び出す.
   *
   * @return 成功時は一覧画面への outcome（faces-redirect=true）
   */
  public String create() {
    createTask.create(CreateTaskCommand.builder().title(title).build());
    FacesContext.getCurrentInstance()
        .addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "登録", "タスクを作成しました"));
    return "/tasks/list.xhtml?faces-redirect=true";
  }
}
```

- [ ] **Step 4: `new.xhtml` を実装**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE html>
<ui:composition
  template="/WEB-INF/templates/main.xhtml"
  xmlns="http://www.w3.org/1999/xhtml"
  xmlns:ui="jakarta.faces.facelets"
  xmlns:h="jakarta.faces.html"
  xmlns:p="http://primefaces.org/ui"
>
  <ui:define name="content">
    <h:form id="formNew">
      <p:panel header="タスク新規作成">
        <!-- メッセージ表示（必須検証・業務エラー）はテンプレート main.xhtml の globalMessages に一本化 -->
        <div class="form-row">
          <p:outputLabel for="title" value="タイトル:" />
          <p:inputText
            id="title"
            value="#{taskFormBean.title}"
            required="true"
            maxlength="100"
            size="40"
          />
        </div>

        <p:commandButton value="作成" action="#{taskFormBean.create}" ajax="false" />
        <p:link outcome="/tasks/list.xhtml" value="キャンセル" />
      </p:panel>
    </h:form>
  </ui:define>
</ui:composition>
```

- [ ] **Step 5: 結合テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast verify -Dit.test=TaskFormBeanIT`
Expected: PASS。

- [ ] **Step 6: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/adapter/in/web/TaskFormBean.java \
        src/main/webapp/tasks/new.xhtml \
        src/test/java/jp/mufg/it/rcb/adapter/in/web/TaskFormBeanIT.java
git commit -m "feat: TaskFormBean と新規作成画面（new.xhtml）を追加"
```

### Task 11: `TaskListBean` + `list.xhtml` + `app.css`（結合テスト）

**Files:**

- Create: `src/main/java/jp/mufg/it/rcb/adapter/in/web/TaskListBean.java`
- Create: `src/main/webapp/tasks/list.xhtml`
- Modify: `src/main/webapp/resources/css/app.css`
- Test: `src/test/java/jp/mufg/it/rcb/adapter/in/web/TaskListBeanIT.java`

**Interfaces:**

- Consumes: `ListTasksUseCase`, `CompleteTaskUseCase`, `TaskSummary`, 検証用 `TaskRepositoryAdapter` / `ListTasksService` / `CompleteTaskService` / `FixedClockStub`。
- Produces: `TaskListBean`（`@Named @ViewScoped`）:
  - `List<TaskSummary> getTasks()`（`@PostConstruct init()` で `refresh()`）。
  - `String complete()` — **完了対象 ID をリクエストパラメータ `taskId` から取得**（method 式引数だと POST 偽装 E2E ができないため。§設計）。`CompleteTaskUseCase#complete(id)` 実行後 `"/tasks/list.xhtml?faces-redirect=true"` を返す。

**設計メモ（E2E 偽装のための ID 受け渡し）:** `list.xhtml` の完了ボタンは `<f:param name="taskId" value="#{t.id}"/>` で ID をリクエストパラメータとして送る。Bean は `FacesContext...getExternalContext().getRequestParameterMap().get("taskId")` で受け取り `Long.parseLong` する。これにより Playwright が POST ボディの `taskId` を書き換えて「存在しない ID の完了要求」を再現でき（Task 15）、かつ JSF の ViewState 整合は保たれる。

- [ ] **Step 1: 失敗するテストを書く**

```java
package jp.mufg.it.rcb.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;
import jp.mufg.it.rcb.adapter.out.persistence.TaskMapper;
import jp.mufg.it.rcb.adapter.out.persistence.TaskRepositoryAdapter;
import jp.mufg.it.rcb.application.service.CompleteTaskService;
import jp.mufg.it.rcb.application.service.ListTasksService;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.shared.test.JpaTestSupport;
import jp.mufg.it.rcb.shared.test.ReflectionTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

// 抑制理由:
//  - checkstyle:AbbreviationAsWordInName: 末尾 "IT" は maven-failsafe の結合テスト命名規約。
/**
 * {@link TaskListBean} の結合テスト（本物 Service / Adapter / H2 まで通す）.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class TaskListBeanIT {

  /** H2 bootstrap で生成する EntityManagerFactory. */
  private EntityManagerFactory emf;

  /** 各テストごとの EntityManager. */
  private EntityManager em;

  /** テスト対象. */
  private TaskListBean bean;

  /** DB 検証・準備用の本物 Adapter（同一 EM 共有）. */
  private TaskRepositoryAdapter adapter;

  /** FacesContext static mock. */
  private MockedStatic<FacesContext> facesCtxMock;

  /** リクエストパラメータ差し替え用の FacesContext モック. */
  private FacesContext mockCtx;

  /** ExternalContext モック（リクエストパラメータマップ提供）. */
  private ExternalContext mockExt;

  /* default */ TaskListBeanIT() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** H2 にスキーマを作成する。 */
  @BeforeAll
  void setUpAll() throws IOException, SQLException {
    emf =
        JpaTestSupport.bootstrapH2(
            "appTestPU",
            "jdbc:h2:mem:task-list-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__init.sql");
  }

  /** EntityManagerFactory を閉じる。 */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** Bean → 本物 Service → 本物 Adapter → H2 を組み立てる。 */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    JpaTestSupport.runInTx(em, () -> em.createQuery("DELETE FROM TaskEntity").executeUpdate());

    adapter = JpaTestSupport.injectEntityManager(new TaskRepositoryAdapter(new TaskMapper()), em);
    final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-07-03T10:00:00Z"));

    final ListTasksService listService = new ListTasksService(adapter);
    final CompleteTaskService completeService = new CompleteTaskService(adapter, clock);
    ReflectionTestSupport.injectField(completeService, "sysLogger", Logger.getLogger("test"));

    bean = new TaskListBean();
    bean.listTasks = listService;
    bean.completeTask = completeService;

    facesCtxMock = Mockito.mockStatic(FacesContext.class);
    mockCtx = Mockito.mock(FacesContext.class);
    mockExt = Mockito.mock(ExternalContext.class);
    Mockito.when(mockCtx.getExternalContext()).thenReturn(mockExt);
    facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(mockCtx);
  }

  /** static mock と EM を解放する。 */
  @AfterEach
  void tearDown() {
    if (facesCtxMock != null) {
      facesCtxMock.close();
    }
    if (em != null && em.isOpen()) {
      em.close();
    }
  }

  /** リクエストパラメータ taskId を差し替えるヘルパ。 */
  private void setTaskIdParam(final String value) {
    Mockito.when(mockExt.getRequestParameterMap()).thenReturn(Map.of("taskId", value));
  }

  /** init() は seed 済みタスクを作成日時降順で読み出す。 */
  @Test
  void initLoadsTasksDescending() {
    JpaTestSupport.runInTx(
        em,
        () -> {
          adapter.save(Task.create("古い", Instant.parse("2026-07-01T09:00:00Z")));
          adapter.save(Task.create("新しい", Instant.parse("2026-07-03T09:00:00Z")));
        });
    em.clear();

    bean.init();
    assertThat(bean.getTasks()).extracting("title").containsExactly("新しい", "古い");
  }

  /** complete() で対象タスクが DONE に遷移する。 */
  @Test
  void completeTransitionsTaskToDone() {
    final Task[] saved = new Task[1];
    JpaTestSupport.runInTx(
        em, () -> saved[0] = adapter.save(Task.create("会議", Instant.parse("2026-07-03T09:00:00Z"))));
    em.clear();
    setTaskIdParam(String.valueOf(saved[0].getId()));

    final String[] outcome = new String[1];
    JpaTestSupport.runInTx(em, () -> outcome[0] = bean.complete());
    assertThat(outcome[0]).isEqualTo("/tasks/list.xhtml?faces-redirect=true");

    em.clear();
    assertThat(adapter.findById(saved[0].getId()).orElseThrow().getStatus())
        .isEqualTo(TaskStatus.DONE);
  }

  /** 既に DONE のタスクを complete() すると MSTBusinessException（回復可・現画面留置）。 */
  @Test
  void completeAlreadyDoneThrowsRecoverable() {
    final Task[] saved = new Task[1];
    JpaTestSupport.runInTx(
        em, () -> saved[0] = adapter.save(Task.create("会議", Instant.parse("2026-07-03T09:00:00Z"))));
    em.clear();
    setTaskIdParam(String.valueOf(saved[0].getId()));
    // 1 回目の完了。
    JpaTestSupport.runInTx(em, () -> bean.complete());
    em.clear();

    // 2 回目（stale 二重完了）は業務エラー。
    assertThatThrownBy(() -> JpaTestSupport.runInTx(em, () -> bean.complete()))
        .isInstanceOf(MSTBusinessException.class);
  }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast verify -Dit.test=TaskListBeanIT`
Expected: FAIL（`TaskListBean` 未存在）。

- [ ] **Step 3: `TaskListBean` を実装**

```java
package jp.mufg.it.rcb.adapter.in.web;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import jp.mufg.it.rcb.application.port.in.CompleteTaskUseCase;
import jp.mufg.it.rcb.application.port.in.ListTasksUseCase;
import jp.mufg.it.rcb.application.port.in.TaskSummary;
import lombok.Getter;

/**
 * タスク一覧画面の Backing Bean.
 *
 * <p>一覧を {@link ListTasksUseCase} で読み出し、行の「完了」ボタンから {@link CompleteTaskUseCase} に委譲する。
 * 完了対象 ID はリクエストパラメータ {@code taskId}（xhtml の {@code <f:param>}）で受け取る。URL 直叩き・POST 偽装でも
 * UseCase 側で対象存在・状態を再評価するため、画面側のボタン抑制は UX 目的に留まる（技術設計書 §6）。
 */
@Named
@ViewScoped
public class TaskListBean implements Serializable {

  private static final long serialVersionUID = 1L;

  /** タスク一覧ユースケース port. */
  @Inject /* default */ ListTasksUseCase listTasks;

  /** タスク完了ユースケース port. */
  @Inject /* default */ CompleteTaskUseCase completeTask;

  /** 一覧表示用のタスク（作成日時降順）. */
  @Getter private List<TaskSummary> tasks;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public TaskListBean() {
    // CDI Bean のため初期化処理は不要。
  }

  /** ビュー生成時に一覧を初期化する. */
  @PostConstruct
  /* default */ void init() {
    refresh();
  }

  /** 一覧を取得し直す. */
  public void refresh() {
    tasks = listTasks.list();
  }

  /**
   * リクエストパラメータ {@code taskId} のタスクを完了する.
   *
   * @return 一覧画面への outcome（faces-redirect=true）
   */
  public String complete() {
    final String raw =
        FacesContext.getCurrentInstance()
            .getExternalContext()
            .getRequestParameterMap()
            .get("taskId");
    completeTask.complete(Long.parseLong(raw));
    return "/tasks/list.xhtml?faces-redirect=true";
  }
}
```

- [ ] **Step 4: `list.xhtml` を実装**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE html>
<ui:composition
  template="/WEB-INF/templates/main.xhtml"
  xmlns="http://www.w3.org/1999/xhtml"
  xmlns:ui="jakarta.faces.facelets"
  xmlns:h="jakarta.faces.html"
  xmlns:f="jakarta.faces.core"
  xmlns:p="http://primefaces.org/ui"
>
  <ui:define name="content">
    <h:form id="listForm">
      <p:dataTable
        value="#{taskListBean.tasks}"
        var="t"
        paginator="true"
        rows="10"
        emptyMessage="タスクがありません"
      >
        <p:column headerText="ID">#{t.id}</p:column>
        <p:column headerText="タイトル">#{t.title}</p:column>
        <p:column headerText="状態">
          <h:outputText
            value="#{t.status}"
            styleClass="status-#{t.status.name().toLowerCase()}"
          />
        </p:column>
        <p:column headerText="作成日時">#{t.createdAt}</p:column>
        <p:column headerText="操作">
          <p:commandButton
            value="完了"
            action="#{taskListBean.complete}"
            ajax="false"
            rendered="#{t.status eq 'TODO'}"
          >
            <f:param name="taskId" value="#{t.id}" />
          </p:commandButton>
        </p:column>
      </p:dataTable>
    </h:form>
  </ui:define>
</ui:composition>
```

- [ ] **Step 5: `app.css` の status クラスを task 用に置き換え**

`app.css` の `.status-pending` / `.status-approved` / `.status-rejected` を削除し、`.status-todo` / `.status-done` を追加する。全体を以下に置き換える。

```css
.content {
  padding: 1rem;
}
.form-row {
  margin-bottom: 1rem;
}
.status-todo {
  color: #b58900;
}
.status-done {
  color: #2aa198;
}
```

- [ ] **Step 6: 結合テストが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pfast verify -Dit.test=TaskListBeanIT`
Expected: PASS（3 テスト成功）。

- [ ] **Step 7: コミット**

```bash
git add src/main/java/jp/mufg/it/rcb/adapter/in/web/TaskListBean.java \
        src/main/webapp/tasks/list.xhtml \
        src/main/webapp/resources/css/app.css \
        src/test/java/jp/mufg/it/rcb/adapter/in/web/TaskListBeanIT.java
git commit -m "feat: TaskListBean と一覧画面（list.xhtml）・完了アクションを追加"
```

---

## Phase 6: 例外配線（faces-config.xml + default.properties）

### Task 12: 回復不可エラーの error.xhtml リダイレクト配線を通す

現状 `faces-config.xml` に `error.page.*` エントリが無く、`default.properties` に `exceptionhandler.unknown-business.*` が無いため、`MSTBusinessNonRecoverException`（HTTP 500）と想定外例外の error.xhtml リダイレクトが未配線（redirect 先 null）になっている。アプリ側リソースのみ修正して通す（ハンドラコード＝社内ライブラリ準拠 IF は変更しない）。

**Files:**

- Modify: `src/main/resources/default.properties`
- Modify: `src/main/webapp/WEB-INF/faces-config.xml`

**Interfaces:**

- Produces: `exceptionhandler.unknown-business.httpstatus=500` により `MSTBusinessNonRecoverException` が 5xx 分岐へ乗り、`faces-config.xml` の `error.page.500` / `error.page.default`（`/error.xhtml`）でリダイレクトされる。E2E（Task 15）で実通過を検証する。

- [ ] **Step 1: `default.properties` にエラーコード定義を追記**

既存の `exceptionhandler.invalid-operation.*` の下に以下を追記する。

```properties

# 回復不可の業務エラー（MSTBusinessNonRecoverException / ErrorCode.UNKNOWN_BUSINESS）。
# httpstatus=500 で 5xx 分岐に乗せ、faces-config.xml の error.page.500 経由で error.xhtml へリダイレクトする。
exceptionhandler.unknown-business.message={0}
exceptionhandler.unknown-business.httpstatus=500

# 想定外システム例外（ExceptionFacesResponseHandler#handleUnknownException が ErrorCode.UNKNOWN で参照）。
# httpstatus はハンドラが 500 をハードコードするため message のみ補完する。
exceptionhandler.unknown.message={0}
```

- [ ] **Step 2: `faces-config.xml` に error.page エントリを追加**

`ExceptionFacesResponseHandler#getRedirectUrlForStatus` は `document.getElementsByTagName("entry")` で `<entry><key>error.page.500</key><value>/error.xhtml</value></entry>` 形式を DOM 走査する。`<application>` 要素内の `<resource-bundle>` の後（`</application>` の直前）に以下を追加する。

```xml
    <!--
      ExceptionFacesResponseHandler が HTTP ステータス別のリダイレクト先を解決するためのエントリ。
      handler は getElementsByTagName("entry") で key=error.page.{status} / error.page.default を走査する。
      回復不可の業務エラー(500)・想定外例外(500) はここで /error.xhtml に着地する。
    -->
    <entry>
      <key>error.page.500</key>
      <value>/error.xhtml</value>
    </entry>
    <entry>
      <key>error.page.default</key>
      <value>/error.xhtml</value>
    </entry>
```

- [ ] **Step 3: WAR がビルド・デプロイできることを確認（スキーマ検証チェック）**

`faces-config.xml` に非標準の `<entry>` 島を追加するため、WildFly デプロイがスキーマ検証で弾かないことを確認する。単体+結合を通し、次いで E2E（Task 15/16 実装後の Task 17 で通す）で実デプロイを検証する。ここではまずビルド成功を確認する。

Run: `scripts/with-env.sh ./mvnw -Pfast clean verify`
Expected: BUILD SUCCESS。**この時点で本体カバレッジ 85% hard gate が初めて全 task コードに対して評価される**。閾値未達なら不足クラスのテストを補う（各 Service/Bean/Mapper/Adapter は Phase 2〜5 でテスト済みのため通過想定）。

> 補足: `<entry>` が faces-config 4.0 スキーマ検証で弾かれた場合の代替は、`web.xml` の `<error-page><error-code>500</error-code><location>/error.xhtml</location></error-page>`（既存で設定済み）に委ねる経路だが、その場合 `ExceptionFacesResponseHandler` の redirect は null のままとなり本 PBI の「faces-config リダイレクト」要件を満たさない。まず `<entry>` 追加でデプロイ可否を E2E（Task 17）で確認し、弾かれた場合のみ本 Step に戻ってハンドラ非改変で成立する配置（例: `faces-config.xml` の別位置、または JBoss の faces-config 検証緩和設定）を調査する。

- [ ] **Step 4: コミット**

```bash
git add src/main/resources/default.properties src/main/webapp/WEB-INF/faces-config.xml
git commit -m "fix: faces-config/default.properties の error.page 配線を追加し回復不可エラーを error.xhtml へ着地"
```

---

## Phase 7: E2E / 性能 / 環境設定

### Task 13: E2E シナリオを task 用に全面書換 + 環境設定の掃除

**Files:**

- Overwrite: `tests/e2e/tests/golden-path.spec.ts`
- Overwrite: `tests/e2e/tests/error-handling.spec.ts`
- Delete: `tests/e2e/tests/authorization.spec.ts`（認可＝認証前提のため廃止）
- Modify: `tests/perf/smoke.js`
- Modify: `.env`, `.env.example`

**Interfaces:**

- Consumes: 稼働中の WildFly（`baseURL=http://localhost:8080/rcb/`）、seed 済み task（`R__dev_seed.sql`）。
- Produces: 正常系（作成→一覧→完了）と例外 3 経路（バリデーション留置 / 二重完了留置 / 存在しない ID 完了→error.xhtml）の E2E。

- [ ] **Step 1: 認可 spec を削除**

```bash
rm tests/e2e/tests/authorization.spec.ts
```

- [ ] **Step 2: `golden-path.spec.ts` を上書き（認証なし・作成→一覧→完了）**

```ts
import { test, expect } from '@playwright/test';

// 認証なしタスク管理サンプルのゴールデンパス:
//   1. 新規作成画面でタイトルを入力して作成 → 一覧へ redirect。
//   2. 一覧に作成したタスクが TODO で表示される。
//   3. その行の「完了」ボタンで完了 → 一覧へ戻り DONE で表示される。
test.describe('Task management golden path', () => {
  test('create a task, see it listed, then complete it', async ({ page }) => {
    const title = `E2E ゴールデンパス ${Date.now()}`;

    await page.goto('tasks/new.xhtml');
    await page.fill('input[id="formNew:title"]', title);
    await page.click('button:has-text("作成")');
    await page.waitForURL('**/tasks/list.xhtml');

    // 作成したタスク行を特定し、状態が TODO であることを確認。
    const row = page.locator('tr', { hasText: title });
    await expect(row).toBeVisible();
    await expect(row.getByText('TODO')).toBeVisible();

    // 完了ボタンを押す（非 ajax サブミット → 一覧へ redirect）。
    await row.locator('button:has-text("完了")').click();
    await page.waitForURL('**/tasks/list.xhtml');

    // 同じタスクが DONE 表示になり、完了ボタンが消える。
    const doneRow = page.locator('tr', { hasText: title });
    await expect(doneRow.getByText('DONE')).toBeVisible();
    await expect(doneRow.locator('button:has-text("完了")')).toHaveCount(0);
  });
});
```

- [ ] **Step 3: `error-handling.spec.ts` を上書き（例外 3 経路）**

```ts
import { test, expect } from '@playwright/test';

// 例外処理からのページ遷移を JSF ライフサイクル経由で実通過させる（本プロジェクトでは E2E でしか通らない経路）。
test.describe('Exception handling (JSF exception handler wiring)', () => {
  // (a) バリデーションエラー: タイトル未入力 → 現画面(new)に留置 + globalMessages。
  test('required-field validation stays on new.xhtml with a global message', async ({ page }) => {
    await page.goto('tasks/new.xhtml');
    await page.click('button:has-text("作成")');
    await expect(page).toHaveURL(/tasks\/new\.xhtml/);
    await expect(page.locator('.ui-messages-error').first()).toBeVisible();
  });

  // (b) 業務エラー(回復可): 2 タブ stale 二重完了 → 現画面(list)に留置 + 業務メッセージ、error.xhtml へ遷移しない。
  test('double-complete stays on list with a business message (not error.xhtml)', async ({
    browser,
  }) => {
    const title = `E2E 二重完了 ${Date.now()}`;

    // 事前に 1 件作成。
    const setup = await browser.newContext();
    const setupPage = await setup.newPage();
    await setupPage.goto('tasks/new.xhtml');
    await setupPage.fill('input[id="formNew:title"]', title);
    await setupPage.click('button:has-text("作成")');
    await setupPage.waitForURL('**/tasks/list.xhtml');
    await setup.close();

    // 2 セッションで同じ一覧（TODO 完了ボタンが見える stale 状態）を開く。
    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await pageA.goto('tasks/list.xhtml');
    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await pageB.goto('tasks/list.xhtml');

    // B が先に完了。
    await pageB.locator('tr', { hasText: title }).locator('button:has-text("完了")').click();
    await pageB.waitForURL('**/tasks/list.xhtml');

    // A が stale な画面で完了 → 業務エラー（回復可）。
    await pageA.locator('tr', { hasText: title }).locator('button:has-text("完了")').click();

    await expect(pageA).toHaveURL(/tasks\/list\.xhtml/);
    await expect(pageA.getByText('既に完了したタスクです')).toBeVisible();
    await expect(pageA.getByText('エラーが発生しました')).toHaveCount(0);

    await ctxA.close();
    await ctxB.close();
  });

  // (c) 業務エラー(回復不可): 存在しない ID への完了 POST 偽装 → faces-config 経由で error.xhtml に遷移。
  test('completing a non-existent id (forged POST) redirects to error.xhtml', async ({ page }) => {
    // まず TODO を 1 件用意（完了ボタンが描画される状態を作る）。
    const title = `E2E 偽装 ${Date.now()}`;
    await page.goto('tasks/new.xhtml');
    await page.fill('input[id="formNew:title"]', title);
    await page.click('button:has-text("作成")');
    await page.waitForURL('**/tasks/list.xhtml');

    // 完了サブミットの POST ボディで taskId を存在しない値に書き換える（POST 偽装の再現）。
    // ViewState 等はそのままに taskId の値のみ差し替えるため JSF ライフサイクルは正常に進み、
    // UseCase が対象不存在を検知して MSTBusinessNonRecoverException を送出する。
    await page.route('**/tasks/list.xhtml', async (route) => {
      const req = route.request();
      if (req.method() === 'POST') {
        const body = req.postData() ?? '';
        const forged = body.replace(/taskId=\d+/, 'taskId=999999999');
        await route.continue({ postData: forged });
      } else {
        await route.continue();
      }
    });

    await page.locator('tr', { hasText: title }).locator('button:has-text("完了")').click();

    // faces-config の error.page.500 経由で error.xhtml に全画面遷移する。
    await expect(page).toHaveURL(/error\.xhtml/);
    await expect(page.getByText('エラーが発生しました')).toBeVisible();
    // 戻りリンクからタスク一覧へ戻れる。
    await page.click('a:has-text("タスク一覧へ戻る")');
    await expect(page).toHaveURL(/tasks\/list\.xhtml/);
  });
});
```

- [ ] **Step 4: `perf/smoke.js` を task 用に修正**

`login.xhtml` を GET していた箇所を `tasks/list.xhtml` に変更する。該当行を以下に置き換える（`loginForm` の body チェックはタスク一覧の存在確認に変更）。

```js
const res = http.get(`${BASE_URL}/tasks/list.xhtml`);
check(res, {
  'tasks/list.xhtml status is 200': (r) => r.status === 200,
  'tasks/list.xhtml has table': (r) => r.body.includes('listForm'),
});
```

- [ ] **Step 5: `.env` / `.env.example` の認証・leave 参照を掃除**

- `.env`: `APP_AUTH_MODE=DEV_LOGIN` の行を削除。`login.xhtml` を前提とするコメント（E2E baseURL 説明など）は `tasks/list.xhtml` 基準に更新。
- `.env.example`: `H2_FILE_PATH=./target/h2/leave-e2e` を `H2_FILE_PATH=./target/h2/app-e2e` に変更（pom.xml の `<e2e.h2.file.path>` が `app-e2e` を使うため名称を合わせる）。

Run: `grep -nE "DEV_LOGIN|leave-e2e|login\.xhtml" .env .env.example`
Expected: 0 件。

- [ ] **Step 6: E2E を実行して 4 シナリオが通ることを確認**

Run: `scripts/with-env.sh ./mvnw -Pe2e verify`
Expected: BUILD SUCCESS。Playwright 4 テスト（golden-path 1 + error-handling 3）が PASS。**この時点で `-Pe2e` の 95% カバレッジ hard gate が評価される**。特に (c) が error.xhtml に着地することで `<entry>` を含む faces-config がデプロイ検証を通過することも確認される（Task 12 Step 3 の懸念点の実証）。

> `<entry>` でデプロイが失敗した場合は Task 12 Step 3 の補足に従い代替配置を調査し、error-handling (c) が error.xhtml に着地する状態を必ず作ること。

- [ ] **Step 7: コミット**

```bash
git add tests/e2e/tests/golden-path.spec.ts tests/e2e/tests/error-handling.spec.ts \
        tests/perf/smoke.js .env .env.example
git add -u tests/e2e/tests/
git commit -m "test: E2E を task 用に全面書換（正常系 + 例外3経路）し環境設定を掃除"
```

---

## Phase 8: ドキュメント更新 と 最終検証

### Task 14: ドキュメントを task / 認証なしへ更新

**Files:**

- Overwrite: `docs/docs/02-requirements/domain-glossary.md`
- Modify: `docs/docs/01-getting-started/strip-sample.md`
- Modify: `docs/docs/04-domain-design/01-domain-model.md`, `02-usecases.md`, `03-business-rules.md`
- Delete: `docs/docs/04-domain-design/sample/`（leave ドメイン設計フォルダ）
- Modify: `docs/docs/05-technical-design.md`
- Delete: `docs/docs/03-adrs/ADR-002-snapshot-vs-port.md`
- Modify: `docs/docs/03-adrs/ADR-001-clean-hexagonal.md`
- Modify: `docs/docs/intro.md`
- Modify: `docs/sidebars.js`
- Modify: `README.md`

**Interfaces:**

- Produces: docusaurus build がグリーンで、leave/認証の記述が task/認証なしに置き換わった状態。AGENTS.md が参照する `domain-glossary.md` が task 用語で構成される。

- [ ] **Step 1: `domain-glossary.md` を task 用語へ全面刷新**

ファイル全体を以下に置き換える。

```markdown
# ドメイン用語集（ユビキタス言語）

| 用語                   | 定義                                                                                                                                                                                     |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| タスク (Task)          | 利用者が管理する作業項目。タイトル・状態・作成日時・完了日時を持つ集約ルート                                                                                                             |
| 未完了 (TODO)          | 作成直後の状態。完了操作が可能な唯一の状態                                                                                                                                               |
| 完了 (DONE)            | 完了操作により遷移する終端状態。完了日時が必ず保全される                                                                                                                                 |
| 完了操作 (Complete)    | TODO のタスクを DONE へ遷移させる操作。既に DONE のタスクには実行できない                                                                                                                |
| タスク一覧 (List)      | 全タスクを作成日時の降順で取得する読み取り操作                                                                                                                                           |
| 業務エラー（回復可）   | ユーザ操作で回復可能な業務ルール違反。`MSTBusinessException` で送出し、現画面に `FacesMessage` を表示して留置する（例: 既に完了したタスクの再完了）                                      |
| 業務エラー（回復不可） | 継続不可能な想定外状態。`MSTBusinessNonRecoverException` で送出し、`/error.xhtml` へ遷移する（例: 削除機能の無い本サンプルで、完了 POST に乗る ID が実在しない＝POST 偽装/データ不整合） |
```

- [ ] **Step 2: `strip-sample.md` を「task サンプル削除 + 認証は既に無い」前提へ書換**

以下を反映する（既存の leave/認証前提の記述を置換）:

- 冒頭の説明を「本ボイラープレートには認証不要のタスク管理サンプル（task）が同梱されている」に変更。
- 「削除対象 1: Java 本体コード」の `rm` を task 用パスに変更: `domain`（Task/TaskStatus）、`application`（port.in の Task 系・port.out/TaskRepositoryPort・service の 3 サービス）、`adapter/out`（TaskEntity/TaskMapper/TaskRepositoryAdapter）、`adapter/in/web`（TaskListBean/TaskFormBean）。**`ClockPort` / `SystemClockAdapter` は汎用のため任意で残す旨を明記**。
- 認証に関する記述（LoginBean を残す・dev-users・AuthenticationFilter 等）を全て削除。「本サンプルは認証機能を持たない」と明記。
- 「削除対象 2: テスト」を task テスト（TaskTest/TaskStatusTest/CreateTaskCommandTest/各 ServiceTest/TaskMapperTest/TaskRepositoryAdapterIT/TaskFormBeanIT/TaskListBeanIT）に変更。残るスモークテスト表から認証系（LoginBeanIT/DevLogin/DevUser）を削除。
- 「削除対象 3」を `db/migration/{V1__init.sql,R__dev_seed.sql}` と `webapp/tasks/` に変更。
- 「削除対象 4: メッセージ ID」を `RCB00001-I`/`RCB00002-I`（task）に変更。
- 「削除対象 5: ドキュメント」を task 用に変更（ADR-002 は既に無いので言及削除）。
- 「修正必要箇所 1」の main.xhtml メニュー・AppConfig を task/認証なしに合わせる。
- E2E に関する記述を task シナリオ（golden-path / error-handling）に更新。

- [ ] **Step 3: `04-domain-design/` の §サンプル例を task に差し替え、sample/ を削除**

```bash
rm -rf docs/docs/04-domain-design/sample
```

`01-domain-model.md` / `02-usecases.md` / `03-business-rules.md` の `### サンプル例` / `:::note サンプル例` バナーの中身を task（Task 集約 / 一覧・作成・完了 UC / 「TODO のみ完了可」「再完了不可」「対象不存在=回復不可」ルール）に書き換える。雛形（テンプレ）セクションはそのまま残す。

- [ ] **Step 4: `05-technical-design.md` の §サンプル例と「（サンプル）」注釈を task に差し替え**

- §サンプル例（クラス配置 / テーブル定義 / 採番 / 部長層コード設定）を task 用に更新、または leave 固有で不要なもの（部長層コード）は削除。
- 「バッキングビーン IT パターン集」の代表クラス列（LeaveListBeanIT 等）を TaskListBeanIT / TaskFormBeanIT に更新。
- §4 エラーハンドリングに「対象不存在の回復可/回復不可の切り分け」を追記: 「通常操作（GET/ブックマーク）での対象不存在は回復可。削除機能を持たない本サンプルで完了 POST に乗る ID が実在しないのは POST 偽装/データ不整合＝回復不可（`error.page.500` 経由で error.xhtml）」。
- 設定取得の例（`app.approval.manager-layer-codes` → `getManagerLayerCodes()`）を削除（task では未使用）。

- [ ] **Step 5: ADR-002 を削除し ADR-001 の §サンプル例を task に更新**

```bash
rm docs/docs/03-adrs/ADR-002-snapshot-vs-port.md
```

`ADR-001-clean-hexagonal.md` の `### サンプル例` にある leave 履歴を task の例に書き換える。

- [ ] **Step 6: `sidebars.js` と `intro.md` を更新**

- `docs/sidebars.js` から `domain-design/sample/leave-domain-model` / `domain-design/sample/leave-usecases` / `adrs/ADR-002-snapshot-vs-port` の entry を削除。`getting-started/strip-sample` は残す。
- `docs/docs/intro.md` の「次のステップ」等で leave/ログインに言及する箇所を task/認証なしに更新。

- [ ] **Step 7: `README.md` を更新**

「サンプルアプリ（適用先で削除）」セクションを task 管理サンプル（一覧・作成・完了、認証なし）に書き換え、ディレクトリ構造図の `domain/ application/ adapter/` 説明を task 向けに更新。leave/ログイン/認証の記述を除去。

- [ ] **Step 8: docusaurus build と残存 grep 確認**

Run: `cd docs && npm run build && cd ..`
Expected: ビルド成功（リンク切れ・存在しない sidebar entry が無い）。

Run: `grep -rniE "休暇|leave|ログイン|認証|承認権" docs/docs README.md | grep -viE "認証なし|認証不要|認証機能を持たない|認証基盤"`
Expected: leave/認証サンプルの実体的な残存が無い（「認証なし/不要」等の否定文脈のみ許容）。残っていれば該当箇所を task/認証なしに修正。

- [ ] **Step 9: コミット**

```bash
git add docs README.md
git add -u docs
git commit -m "docs: ドキュメントを task サンプル・認証なし前提へ全面更新"
```

### Task 15: 最終検証・PBI 更新・push

**Files:**

- Modify: `PBI.md`

**Interfaces:**

- Consumes: Phase 1〜8 の全成果。
- Produces: 全ゲート green の最終状態を main に push。

- [ ] **Step 1: 全体の残存参照を最終 grep**

Run:

```bash
grep -rniE "leave|dev-users|login\.xhtml|manager-layer|approval|DEV_LOGIN|LeaveRequest|LoginBean|AuthenticationFilter" \
  src tests .env .env.example pom.xml --include="*.java" --include="*.xhtml" \
  --include="*.properties" --include="*.xml" --include="*.ts" --include="*.js" \
  | grep -vE "userinfo/(context|dto)"
```

Expected: 0 件（社内ライブラリ準拠 IF の `userinfo` パッケージ本体を除く）。残れば修正してから続行。

- [ ] **Step 2: 単体 + 結合 + カバレッジ（本体 85%）**

Run: `scripts/with-env.sh ./mvnw -Pfast clean verify`
Expected: BUILD SUCCESS（jacoco:check 本体 85% LINE/BRANCH 通過）。

- [ ] **Step 3: E2E + カバレッジ（95%）**

Run: `scripts/with-env.sh ./mvnw -Pe2e verify`
Expected: BUILD SUCCESS（Playwright 4 テスト PASS、`-Pe2e` 95% hard gate 通過）。

- [ ] **Step 4: CI 相当のカバレッジチェック**

Run: `scripts/with-env.sh ./mvnw -Pci-mr jacoco:report jacoco:check`
Expected: BUILD SUCCESS。

- [ ] **Step 5: docusaurus build**

Run: `cd docs && npm run build && cd ..`
Expected: ビルド成功。

- [ ] **Step 6: `PBI.md` の ID:1 を done に更新**

`# ID: 1` の `- ステータス: to do` を `- ステータス: done` に変更する。

- [ ] **Step 7: 最終コミットと push**

```bash
git add PBI.md
git commit -m "chore: PBI ID:1（サンプルアプリ簡略化）を完了"
# main で作業している場合はブランチを切ってから push する
git rev-parse --abbrev-ref HEAD   # 現在ブランチ確認
# main の場合:
git switch -c feature/task-sample-app
git push -u origin feature/task-sample-app
```

Expected: pre-push フック（`-Pfast clean verify` / 静的解析 / 保護パスガード）が全て green で push 成功。

---

## Self-Review（計画作成者によるスペック突合）

**1. スペック網羅性:**

- §2.2 削除対象 → Task 1 で全削除。§2.5 残存参照（perf/persistence/env/pom SnakeYAML）→ Task 1(persistence.xml)・Task 13(perf/env)・Task 15(最終 grep)でカバー。**pom.xml の SnakeYAML 依存削除** は Task 1 の grep では検出されるが明示タスク化されていない → Task 13 Step 5 の後、または Task 15 Step 1 の grep で `pom.xml` の `snakeyaml` 参照を検出したら削除する旨を運用で担保。（下記「補足」参照）
- §3 レイヤ構成 → Task 2〜11 で全クラス作成。
- §6 例外4経路 → Task 6(サービス)・Task 11(Bean)・Task 12(配線)・Task 13(E2E)でカバー。
- §7 ロギング（RCB00001-I/00002-I）→ Task 1(messages)・Task 5/6(発火)。
- §8 共通基盤改変 → Task 1（AccessLogFilter/web.xml/main.xhtml/error.xhtml）。
- §10 テスト対応表 → 各 Task にテスト同梱。
- §11 ドキュメント → Task 14。
- §12 DoD → Task 15。

**2. プレースホルダscan:** コード Step は全て実コードを掲載済み。ドキュメント Task 14 は散文のため「何をどう書くか」を具体指示（glossary は全文、他は編集方針＋確認 grep）。

**3. 型整合性:** `Instant`（createdAt/completedAt/ClockPort）で統一。`TaskRepositoryPort`（save/findById(long)/findAllOrderByCreatedAtDesc）は Task 4 定義と Task 7/9/11 使用で一致。`complete()` は Bean が request param 経由、UseCase は `complete(long)` で一貫。`CreateTaskUseCase#create(void)` は Bean/Service で一致。

**補足（実装時に必ず処理）:** `pom.xml` の SnakeYAML 依存（コメント「dev-users.yml の読み込み用」）は `DevLoginAuthenticationAdapter` 削除で不要になる。Task 1 完了後に `grep -n "snakeyaml\|yaml" pom.xml` で確認し、他用途が無ければ `<dependency>` ブロックを削除して `scripts/with-env.sh ./mvnw -Pfast test` の再確認まで行う（削除は Task 1 のコミットに含める）。
