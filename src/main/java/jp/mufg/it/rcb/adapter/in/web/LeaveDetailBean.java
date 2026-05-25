package jp.mufg.it.rcb.adapter.in.web;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import jp.mufg.it.rcb.application.port.in.ApproveLeaveCommand;
import jp.mufg.it.rcb.application.port.in.ApproveLeaveUseCase;
import jp.mufg.it.rcb.application.port.in.FindLeaveUseCase;
import jp.mufg.it.rcb.application.port.in.LeaveRequestDetail;
import jp.mufg.it.rcb.application.port.in.RejectLeaveCommand;
import jp.mufg.it.rcb.application.port.in.RejectLeaveUseCase;
import lombok.Getter;
import lombok.Setter;

/**
 * 休暇申請詳細画面の Backing Bean.
 *
 * <p>{@code f:viewParam} で受け取った id を元に {@link FindLeaveUseCase} で詳細を取得し、 詳細表示および承認 /
 * 却下アクションを提供する。業務エラーは {@code ExceptionFacesResponseHandler} が JSF ライフサイクル経由で 一括ハンドリングし、現画面に
 * FacesMessage を表示するため、本 Bean では catch しない。成功時は {@code /leaves/list.xhtml} に redirect する。
 */
@Named
@ViewScoped
public class LeaveDetailBean implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 休暇申請詳細取得ユースケース port. */
  @Inject /* default */ FindLeaveUseCase findLeave;

  /** 休暇承認ユースケース port. */
  @Inject /* default */ ApproveLeaveUseCase approveLeave;

  /** 休暇却下ユースケース port. */
  @Inject /* default */ RejectLeaveUseCase rejectLeave;

  /** {@code f:viewParam} で受け取る休暇申請 ID. */
  @Getter @Setter private Long id;

  /** 取得した休暇申請の詳細（取得失敗時は null のまま）. */
  @Getter private LeaveRequestDetail detail;

  /** 承認 / 却下時に入力する判断コメント. */
  @Getter @Setter private String judgeComment;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public LeaveDetailBean() {
    // CDI Bean のため初期化処理は不要.
  }

  /**
   * {@code f:viewAction} から呼び出され、id を元に詳細を取得する.
   *
   * <p>id が null の場合は早期 return する（直接アクセス時のフォールバック）。 業務エラーは ExceptionFacesResponseHandler
   * が一括処理するため、本メソッドでは catch しない（{@code detail} は null のまま画面に form を描画させない）。
   */
  public void load() {
    if (id == null) {
      return;
    }
    detail = findLeave.find(id);
  }

  /**
   * 承認アクション.
   *
   * <p>業務エラーは ExceptionFacesResponseHandler が一括処理するため、本メソッドでは catch しない（JSF が action navigation を
   * 実行せず現画面に留まる）。
   *
   * @return 成功時は一覧画面への outcome（faces-redirect=true）
   */
  public String approve() {
    approveLeave.approve(
        ApproveLeaveCommand.builder().leaveRequestId(id).comment(judgeComment).build());
    return "/leaves/list.xhtml?faces-redirect=true";
  }

  /**
   * 却下アクション.
   *
   * <p>BR-PRE-004 によりコメント必須。UseCase 側でも Bean Validation で正本検証される（{@link
   * RejectLeaveCommand#getComment()} に {@code @NotBlank}）が、UX 補助として Backing Bean 側でも事前チェックして
   * FacesMessage で警告表示する。業務エラーは ExceptionFacesResponseHandler が一括処理するため、本メソッドでは catch しない。
   *
   * @return 成功時は一覧画面への outcome（faces-redirect=true）、入力エラー時は null で現画面に留まる
   */
  public String reject() {
    if (judgeComment == null || judgeComment.isBlank()) {
      FacesContext.getCurrentInstance()
          .addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "入力エラー", "却下時はコメント必須です"));
      return null;
    }
    rejectLeave.reject(
        RejectLeaveCommand.builder().leaveRequestId(id).comment(judgeComment).build());
    return "/leaves/list.xhtml?faces-redirect=true";
  }
}
