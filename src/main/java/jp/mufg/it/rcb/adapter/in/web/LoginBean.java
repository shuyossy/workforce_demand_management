package jp.mufg.it.rcb.adapter.in.web;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;
import jp.mufg.it.rcb.shared.security.AuthenticationPort;
import jp.mufg.it.rcb.shared.security.DevUser;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import lombok.Getter;
import lombok.Setter;

// PMD 抑制理由:
//  - NonSerializableClass: {@code sysLogger} は CDI が注入する Logger プロキシで、
//    @ViewScoped ビューパッシベーション時はコンテナがプロキシ参照を再解決するため
//    実害がない。サービス層 (ApplyLeaveService 等) と同じ注入パターンを Backing Bean
//    でも踏襲する目的で transient ではなく抑制を選択する。
/**
 * 開発用ログイン画面の Backing Bean.
 *
 * <p>{@code dev-users.yml} に列挙された開発ユーザをプルダウンで提示し、 選択された empNum を {@link AuthenticationPort} 経由で認証して
 * {@link UserInfoContext} に保持する。 ログアウト時はセッションを無効化し、ログイン画面へ戻る。
 */
@Named
@ViewScoped
@SuppressWarnings("PMD.NonSerializableClass")
public class LoginBean implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 認証 port（{@code dev-users.yml} を参照する {@code DevLoginAuthenticationAdapter} が実装）. */
  @Inject /* default */ AuthenticationPort auth;

  /** ログイン中ユーザを保持するセッションスコープのコンテキスト. */
  @Inject /* default */ UserInfoContext userInfoContext;

  /**
   * 業務イベント用 Logger（messages.properties をカスタム Formatter 経由で解決）.
   *
   * <p>パッケージ private 可視性は同パッケージのテストから直接 mock を差し込めるよう意図的に採用している。
   */
  @Inject
  @InjectLogger(LoggerType.SYSTEM)
  /* default */ Logger sysLogger;

  /** ログイン候補ユーザ一覧（プルダウン表示用）. */
  @Getter /* default */ List<DevUser> availableUsers;

  /** プルダウンで選択された社員番号. */
  @Getter @Setter private String selectedEmpNum;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public LoginBean() {
    // CDI Bean のため初期化処理は不要.
  }

  /** {@link AuthenticationPort#listAvailableUsers()} の結果でプルダウン候補を初期化する. */
  @PostConstruct
  /* default */ void init() {
    availableUsers = auth.listAvailableUsers();
  }

  /**
   * 画面表示用にログイン中ユーザの氏名を返す.
   *
   * @return ログイン中ユーザの氏名（未ログイン時は空文字）
   */
  public String getCurrentUserName() {
    final UserDto user = userInfoContext.getUser();
    return user == null ? "" : user.getName();
  }

  /**
   * 選択された empNum で認証し、成功時は休暇一覧へ、失敗時は警告メッセージを表示する.
   *
   * @return 遷移先の outcome（失敗時は null で現画面に留まる）
   */
  public String doLogin() {
    final Optional<UserDto> user = auth.authenticate(selectedEmpNum);
    if (user.isEmpty()) {
      FacesContext.getCurrentInstance()
          .addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "ログイン失敗", "ユーザが見つかりません"));
      return null;
    }
    userInfoContext.setUser(user.get());
    // PMD GuardLogStatement: Object[] パラメータ配列生成を回避するため Level ガードを挟む.
    if (sysLogger.isLoggable(Level.INFO)) {
      sysLogger.log(Level.INFO, "RCB00004-I", new Object[] {selectedEmpNum});
    }
    return "/leaves/list.xhtml?faces-redirect=true";
  }

  /**
   * セッションを破棄してログイン画面へ戻る.
   *
   * @return ログイン画面への outcome（faces-redirect=true）
   */
  public String logout() {
    final UserDto loggedInUser = userInfoContext.getUser();
    final String empNum = loggedInUser == null ? "-" : loggedInUser.getEmpNum();
    userInfoContext.setUser(null);
    FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
    // PMD GuardLogStatement: Object[] パラメータ配列生成を回避するため Level ガードを挟む.
    if (sysLogger.isLoggable(Level.INFO)) {
      sysLogger.log(Level.INFO, "RCB00005-I", new Object[] {empNum});
    }
    return "/login.xhtml?faces-redirect=true";
  }
}
