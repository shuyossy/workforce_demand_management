package jp.mufg.it.rcb.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.shared.security.DevLoginAuthenticationAdapter;
import jp.mufg.it.rcb.shared.test.ReflectionTestSupport;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

// 抑制理由:
//  - checkstyle:AbbreviationAsWordInName: failsafe 標準命名 (*IT) のため。
/**
 * {@link LoginBean} の結合テスト.
 *
 * <p>本物の {@link DevLoginAuthenticationAdapter}（{@code dev-users.yml} 読み込み）と本物の {@link
 * UserInfoContext} を通じて、ログイン候補一覧取得とログイン状態遷移を検証する。FacesContext は static mock。DB は使わないが、Bean →
 * Service → 設定ファイル読込までの 結合経路を担保する点でリポジトリ系の IT と同じ位置づけ。
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class LoginBeanIT {

  /** テスト対象（本物の Bean インスタンス）. */
  private LoginBean bean;

  /** ログイン状態を保持するセッションコンテキスト（本物）. */
  private UserInfoContext userInfoContext;

  /** FacesContext.getCurrentInstance() を差し替える static mock. */
  private MockedStatic<FacesContext> facesCtxMock;

  /** FacesContext モックインスタンス（addMessage / invalidateSession の検証用）. */
  private FacesContext mockFacesCtx;

  /** ExternalContext モック（logout の invalidateSession 経路用）. */
  private ExternalContext mockExtCtx;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LoginBeanIT() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** 各テストの前に Bean → 本物 DevLoginAuthenticationAdapter → dev-users.yml 経路を組み立てる. */
  @BeforeEach
  void setUp() {
    final DevLoginAuthenticationAdapter authAdapter = new DevLoginAuthenticationAdapter();
    // @ConfigProperty 解決経路は CDI 経由なのでテストでは package-private フィールドへ直接代入する.
    ReflectionTestSupport.injectField(authAdapter, "devUsersPath", "dev-users.yml");
    // @PostConstruct を手動呼び出し（CDI コンテナを起動しないため）.
    invokeInit(authAdapter);

    userInfoContext = new UserInfoContext();

    bean = new LoginBean();
    bean.auth = authAdapter;
    bean.userInfoContext = userInfoContext;
    bean.sysLogger = Logger.getLogger("test");

    facesCtxMock = Mockito.mockStatic(FacesContext.class);
    mockFacesCtx = Mockito.mock(FacesContext.class);
    mockExtCtx = Mockito.mock(ExternalContext.class);
    Mockito.when(mockFacesCtx.getExternalContext()).thenReturn(mockExtCtx);
    facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(mockFacesCtx);
  }

  /** 各テスト後に static mock を解放する. */
  @AfterEach
  void tearDown() {
    if (facesCtxMock != null) {
      facesCtxMock.close();
    }
  }

  /** {@code @PostConstruct init()} で dev-users.yml から候補ユーザ一覧が読み込まれることを確認する. */
  @Test
  void initLoadsAvailableUsersFromDevUsersYaml() {
    bean.init();

    assertThat(bean.getAvailableUsers()).hasSize(4);
    assertThat(bean.getAvailableUsers())
        .extracting(u -> u.getEmpNum())
        .containsExactly("E0001", "E0002", "E0003", "E0004");
  }

  /** doLogin() 成功時に UserInfoContext へ認証済みユーザが設定され、一覧画面の outcome を返すことを確認する. */
  @Test
  void doLoginSetsUserInfoContextOnSuccess() {
    bean.init();
    bean.setSelectedEmpNum("E0002");

    final String outcome = bean.doLogin();

    assertThat(outcome).isEqualTo("/leaves/list.xhtml?faces-redirect=true");
    assertThat(userInfoContext.getUser()).isNotNull();
    assertThat(userInfoContext.getUser().getEmpNum()).isEqualTo("E0002");
    assertThat(userInfoContext.getUser().getMainUserPositionDto().getOrgId()).isEqualTo("ORG-001");
  }

  /** doLogin() で存在しない empNum が指定された場合は FacesMessage 警告を出力し null を返すことを確認する. */
  @Test
  void doLoginShowsWarningWhenUserNotFound() {
    bean.init();
    bean.setSelectedEmpNum("E9999");

    final String outcome = bean.doLogin();

    assertThat(outcome).isNull();
    assertThat(userInfoContext.getUser()).isNull();
    final ArgumentCaptor<FacesMessage> captor = ArgumentCaptor.forClass(FacesMessage.class);
    Mockito.verify(mockFacesCtx).addMessage(Mockito.eq(null), captor.capture());
    assertThat(captor.getValue().getSeverity()).isEqualTo(FacesMessage.SEVERITY_WARN);
  }

  /** getCurrentUserName() は未ログイン時に空文字を返し、ログイン後はユーザ名を返すことを確認する. */
  @Test
  void currentUserNameReflectsLoginState() {
    bean.init();
    assertThat(bean.getCurrentUserName()).isEmpty();

    bean.setSelectedEmpNum("E0001");
    bean.doLogin();
    assertThat(bean.getCurrentUserName()).isEqualTo("山田 太郎");
  }

  /** logout() でセッションが invalidate され UserInfoContext がクリアされることを確認する. */
  @Test
  void logoutClearsUserAndInvalidatesSession() {
    bean.init();
    bean.setSelectedEmpNum("E0002");
    bean.doLogin();
    Mockito.clearInvocations(mockFacesCtx, mockExtCtx);

    final String outcome = bean.logout();

    assertThat(outcome).isEqualTo("/login.xhtml?faces-redirect=true");
    assertThat(userInfoContext.getUser()).isNull();
    Mockito.verify(mockExtCtx).invalidateSession();
  }

  /** logout() を未ログイン状態で呼んでも例外なくログイン画面 outcome を返すことを確認する（user == null 経路）. */
  @Test
  void logoutWhenNotLoggedInReturnsLoginOutcome() {
    bean.init();

    final String outcome = bean.logout();

    assertThat(outcome).isEqualTo("/login.xhtml?faces-redirect=true");
    assertThat(userInfoContext.getUser()).isNull();
    Mockito.verify(mockExtCtx).invalidateSession();
  }

  /** sysLogger.isLoggable(INFO) が false のときログ出力をスキップしつつ doLogin / logout が正常完遂することを確認する. */
  @Test
  void doLoginAndLogoutSkipLoggingWhenLoggerNotLoggable() {
    final Logger silentLogger = Mockito.mock(Logger.class);
    Mockito.when(silentLogger.isLoggable(Level.INFO)).thenReturn(false);
    bean.sysLogger = silentLogger;
    bean.init();
    bean.setSelectedEmpNum("E0001");

    final String loginOutcome = bean.doLogin();
    final String logoutOutcome = bean.logout();

    assertThat(loginOutcome).isEqualTo("/leaves/list.xhtml?faces-redirect=true");
    assertThat(logoutOutcome).isEqualTo("/login.xhtml?faces-redirect=true");
    Mockito.verify(silentLogger, Mockito.never())
        .log(Mockito.any(Level.class), Mockito.anyString(), Mockito.any(Object[].class));
  }

  /**
   * DevLoginAuthenticationAdapter の package-private {@code init()} をリフレクションで呼び出す.
   *
   * @param adapter 初期化対象の adapter
   */
  private void invokeInit(final DevLoginAuthenticationAdapter adapter) {
    try {
      final java.lang.reflect.Method method =
          DevLoginAuthenticationAdapter.class.getDeclaredMethod("init");
      method.setAccessible(true);
      method.invoke(adapter);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to invoke init()", e);
    }
  }
}
