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
 * <p>一覧を {@link ListTasksUseCase} で読み出し、行の「完了」ボタンから {@link CompleteTaskUseCase} に委譲する。 完了対象 ID
 * はリクエストパラメータ {@code taskId}（xhtml の {@code <f:param>}）で受け取る。URL 直叩き・POST 偽装でも UseCase
 * 側で対象存在・状態を再評価するため、画面側のボタン抑制は UX 目的に留まる（技術設計書 §6）。
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
