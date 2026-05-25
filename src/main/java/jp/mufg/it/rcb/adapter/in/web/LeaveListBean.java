package jp.mufg.it.rcb.adapter.in.web;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;
import jp.mufg.it.rcb.application.port.in.LeaveRequestSummary;
import jp.mufg.it.rcb.application.port.in.ListLeavesCommand;
import jp.mufg.it.rcb.application.port.in.ListLeavesUseCase;
import lombok.Getter;

/**
 * 休暇申請一覧画面の Backing Bean.
 *
 * <p>「自分の申請」「承認待ち」の 2 タブ表示用に、{@link ListLeavesUseCase} を MINE / APPROVABLE の 2
 * 度呼び出して結果を保持する。{@code @ViewScoped} のため画面再表示や AJAX 経由の {@link #refresh()} 呼び出しで都度最新化される。
 */
@Named
@ViewScoped
public class LeaveListBean implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 休暇申請一覧ユースケース port（MINE / APPROVABLE 双方の取得に再利用）. */
  @Inject /* default */ ListLeavesUseCase listLeaves;

  /** 「自分の申請」タブ表示用の一覧（MINE スコープ）. */
  @Getter private List<LeaveRequestSummary> myLeaves;

  /** 「承認待ち」タブ表示用の一覧（APPROVABLE スコープ）. */
  @Getter private List<LeaveRequestSummary> approvableLeaves;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public LeaveListBean() {
    // CDI Bean のため初期化処理は不要.
  }

  /** ビュー生成時に {@link #refresh()} を呼び出して両タブの一覧を初期化する. */
  @PostConstruct
  /* default */ void init() {
    refresh();
  }

  /** MINE / APPROVABLE の 2 スコープで一覧を取得し直す（更新ボタン等から再利用想定）. */
  public void refresh() {
    myLeaves =
        listLeaves.list(ListLeavesCommand.builder().scope(ListLeavesCommand.Scope.MINE).build());
    approvableLeaves =
        listLeaves.list(
            ListLeavesCommand.builder().scope(ListLeavesCommand.Scope.APPROVABLE).build());
  }
}
