package jp.mufg.it.rcb.adapter.in.web;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;
import jp.mufg.it.rcb.application.port.in.ApplyLeaveCommand;
import jp.mufg.it.rcb.application.port.in.ApplyLeaveUseCase;
import jp.mufg.it.rcb.domain.model.LeaveType;
import lombok.Getter;
import lombok.Setter;

/**
 * 休暇申請フォーム画面の Backing Bean.
 *
 * <p>申請種別 / 期間 / 理由を入力させ、{@link ApplyLeaveUseCase} に委譲する。 業務エラーは ExceptionFacesResponseHandler が
 * JSF ライフサイクル経由で一括ハンドリングし、現画面に FacesMessage を表示するため、本 Bean では catch しない。 成功時は {@code
 * /leaves/list.xhtml} に redirect する。
 */
@Named
@ViewScoped
public class LeaveFormBean implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 休暇申請ユースケース port. */
  @Inject /* default */ ApplyLeaveUseCase applyLeave;

  /** 休暇種別（デフォルトは有給）. */
  @Getter @Setter private LeaveType leaveType = LeaveType.PAID;

  /** 期間の開始日. */
  @Getter @Setter private LocalDate startDate;

  /** 期間の終了日. */
  @Getter @Setter private LocalDate endDate;

  /** 申請理由. */
  @Getter @Setter private String reason;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public LeaveFormBean() {
    // CDI Bean のため初期化処理は不要.
  }

  /**
   * 休暇種別プルダウンの選択肢を返す.
   *
   * @return {@link LeaveType} の全要素
   */
  public LeaveType[] getLeaveTypes() {
    return LeaveType.values();
  }

  /**
   * 入力値で {@link ApplyLeaveUseCase} を呼び出す.
   *
   * <p>業務エラーは ExceptionFacesResponseHandler が一括処理するため、本メソッドでは catch しない（JSF が action navigation を
   * 実行せず現画面に留まる）。
   *
   * @return 成功時は一覧画面への outcome（faces-redirect=true）
   */
  public String submit() {
    applyLeave.apply(
        ApplyLeaveCommand.builder()
            .leaveType(leaveType)
            .startDate(startDate)
            .endDate(endDate)
            .reason(reason)
            .build());
    FacesContext.getCurrentInstance()
        .addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "受付", "申請を受け付けました"));
    return "/leaves/list.xhtml?faces-redirect=true";
  }
}
