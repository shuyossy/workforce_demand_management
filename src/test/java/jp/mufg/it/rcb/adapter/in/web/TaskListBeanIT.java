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
/** {@link TaskListBean} の結合テスト（本物 Service / Adapter / H2 まで通す）. */
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

  /** ExternalContext モック（リクエストパラメータマップ提供）. */
  private ExternalContext mockExt;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ TaskListBeanIT() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** H2 にスキーマを作成する. */
  @BeforeAll
  void setUpAll() throws IOException, SQLException {
    emf =
        JpaTestSupport.bootstrapH2(
            "appTestPU",
            "jdbc:h2:mem:task-list-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__init.sql");
  }

  /** EntityManagerFactory を閉じる. */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** Bean から本物 Service / 本物 Adapter / H2 までを組み立てる. */
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
    final FacesContext mockCtx = Mockito.mock(FacesContext.class);
    mockExt = Mockito.mock(ExternalContext.class);
    Mockito.when(mockCtx.getExternalContext()).thenReturn(mockExt);
    facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(mockCtx);
  }

  /** static mock と EM を解放する. */
  @AfterEach
  void tearDown() {
    if (facesCtxMock != null) {
      facesCtxMock.close();
    }
    if (em != null && em.isOpen()) {
      em.close();
    }
  }

  /** リクエストパラメータ taskId を差し替えるヘルパ. */
  private void setTaskIdParam(final String value) {
    Mockito.when(mockExt.getRequestParameterMap()).thenReturn(Map.of("taskId", value));
  }

  /** init() は seed 済みタスクを作成日時降順で読み出す. */
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

  /** complete() で対象タスクが DONE に遷移する. */
  @Test
  void completeTransitionsTaskToDone() {
    final Task[] saved = new Task[1];
    JpaTestSupport.runInTx(
        em,
        () -> saved[0] = adapter.save(Task.create("会議", Instant.parse("2026-07-03T09:00:00Z"))));
    em.clear();
    setTaskIdParam(String.valueOf(saved[0].getId()));

    final String[] outcome = new String[1];
    JpaTestSupport.runInTx(em, () -> outcome[0] = bean.complete());
    assertThat(outcome[0]).isEqualTo("/tasks/list.xhtml?faces-redirect=true");

    em.clear();
    assertThat(adapter.findById(saved[0].getId()).orElseThrow().getStatus())
        .isEqualTo(TaskStatus.DONE);
  }

  /** 既に DONE のタスクを complete() すると MSTBusinessException（回復可・現画面留置）となる. */
  @Test
  void completeAlreadyDoneThrowsRecoverable() {
    final Task[] saved = new Task[1];
    JpaTestSupport.runInTx(
        em,
        () -> saved[0] = adapter.save(Task.create("会議", Instant.parse("2026-07-03T09:00:00Z"))));
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
