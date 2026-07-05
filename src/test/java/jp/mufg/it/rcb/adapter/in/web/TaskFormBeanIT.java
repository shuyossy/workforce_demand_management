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
/** {@link TaskFormBean} の結合テスト（本物 Service / Adapter / H2 まで通す）. */
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

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ TaskFormBeanIT() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** H2 にスキーマを作成する. */
  @BeforeAll
  void setUpAll() throws IOException, SQLException {
    emf =
        JpaTestSupport.bootstrapH2(
            "appTestPU",
            "jdbc:h2:mem:task-form-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__init.sql");
  }

  /** EntityManagerFactory を閉じる. */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** Bean → 本物 Service → 本物 Adapter → H2 を組み立てる. */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    JpaTestSupport.runInTx(em, () -> em.createQuery("DELETE FROM TaskEntity").executeUpdate());

    verifyAdapter =
        JpaTestSupport.injectEntityManager(new TaskRepositoryAdapter(new TaskMapper()), em);
    final CreateTaskService service =
        new CreateTaskService(
            verifyAdapter, new FixedClockStub(Instant.parse("2026-07-03T09:00:00Z")));
    ReflectionTestSupport.injectField(service, "sysLogger", Logger.getLogger("test"));

    bean = new TaskFormBean();
    bean.createTask = service;

    facesCtxMock = Mockito.mockStatic(FacesContext.class);
    facesCtxMock
        .when(FacesContext::getCurrentInstance)
        .thenReturn(Mockito.mock(FacesContext.class));
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

  /** create() で H2 に TODO タスクが作成され、一覧画面 outcome が返る. */
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
