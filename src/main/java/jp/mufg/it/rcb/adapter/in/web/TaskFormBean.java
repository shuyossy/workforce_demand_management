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
 * <p>タイトルを入力させ {@link CreateTaskUseCase} に委譲する。業務エラー・バリデーション違反は ExceptionFacesResponseHandler / JSF
 * が現画面に FacesMessage を表示するため本 Bean では catch しない。 成功時は {@code /tasks/list.xhtml} に redirect する。
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
