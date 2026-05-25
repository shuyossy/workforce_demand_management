package jp.mufg.it.rcb.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.faces.context.FacesContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;
import jp.mufg.it.rcb.adapter.out.persistence.LeaveRepositoryAdapter;
import jp.mufg.it.rcb.adapter.out.persistence.LeaveRequestMapper;
import jp.mufg.it.rcb.application.service.ApplyLeaveService;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.application.service.support.UserInfoContextFactory;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import jp.mufg.it.rcb.shared.test.JpaTestSupport;
import jp.mufg.it.rcb.shared.test.ReflectionTestSupport;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

// 抑制理由:
//  - checkstyle:AbbreviationAsWordInName: クラス名末尾の "IT" は maven-failsafe が結合テストを識別する
//    標準的な命名規約（surefire は *Test、failsafe は *IT）であり、Google Java Style の連続
//    大文字数 1 までという規則と構造的に衝突する。AGENTS.md / rules/README.md の方針上、
//    failsafe 標準命名を優先し、本クラス限定で抑制する。
/**
 * {@link LeaveFormBean} の結合テスト.
 *
 * <p>本物の {@link ApplyLeaveService} と本物の {@link LeaveRepositoryAdapter} を経由して H2 まで実際に保存することを検証する。
 * {@link FacesContext#getCurrentInstance()} は JSF runtime を起動しないため static mock で差し替える。CDI 配線の正しさは
 * Playwright E2E で担保される。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class LeaveFormBeanIT {

  /** H2 + Hibernate standalone bootstrap で生成する EntityManagerFactory. */
  private EntityManagerFactory emf;

  /** 各テストごとに払い出す EntityManager. */
  private EntityManager em;

  /** テスト対象（本物の Bean インスタンス）. */
  private LeaveFormBean bean;

  /** DB 検証用に同じ EM を共有する本物の Repository Adapter. */
  private LeaveRepositoryAdapter verifyAdapter;

  /** FacesContext.getCurrentInstance() を差し替える static mock. */
  private MockedStatic<FacesContext> facesCtxMock;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeaveFormBeanIT() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * H2 にスキーマを作成し EntityManagerFactory を生成する.
   *
   * @throws IOException DDL ファイル読み込み失敗
   * @throws SQLException DDL 実行失敗
   */
  @BeforeAll
  void setUpAll() throws IOException, SQLException {
    emf =
        JpaTestSupport.bootstrapH2(
            "appTestPU",
            "jdbc:h2:mem:leave-form-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__create_leave_request.sql");
  }

  /** EntityManagerFactory を閉じる. */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** 各テストの前に Bean → 本物 Service → 本物 Adapter → H2 の経路を組み立てる. */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    JpaTestSupport.runInTx(
        em, () -> em.createQuery("DELETE FROM LeaveRequestEntity").executeUpdate());

    final LeaveRepositoryAdapter realAdapter =
        JpaTestSupport.injectEntityManager(
            new LeaveRepositoryAdapter(new LeaveRequestMapper()), em);
    verifyAdapter = realAdapter;

    final UserInfoContext userCtx =
        UserInfoContextFactory.create("E0001", "山田 太郎", "ORG-001", "リテール開発部 第一課", "S1");
    final ApplyLeaveService realService =
        new ApplyLeaveService(
            realAdapter, new FixedClockStub(Instant.parse("2026-05-20T09:00:00Z")), userCtx);
    ReflectionTestSupport.injectField(realService, "sysLogger", Logger.getLogger("test"));

    bean = new LeaveFormBean();
    bean.applyLeave = realService;

    facesCtxMock = Mockito.mockStatic(FacesContext.class);
    final FacesContext mockCtx = Mockito.mock(FacesContext.class);
    facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(mockCtx);
  }

  /** 各テスト後に static mock と EM を解放する. */
  @AfterEach
  void tearDown() {
    if (facesCtxMock != null) {
      facesCtxMock.close();
    }
    if (em != null && em.isOpen()) {
      em.close();
    }
  }

  /** submit() で H2 に PENDING 行が作成され、一覧画面 outcome が返ることを確認する. */
  @Test
  void submitPersistsPendingRequest() {
    bean.setLeaveType(LeaveType.PAID);
    bean.setStartDate(LocalDate.of(2026, 6, 1));
    bean.setEndDate(LocalDate.of(2026, 6, 3));
    bean.setReason("私用");

    final String[] outcomeHolder = new String[1];
    JpaTestSupport.runInTx(em, () -> outcomeHolder[0] = bean.submit());

    assertThat(outcomeHolder[0]).isEqualTo("/leaves/list.xhtml?faces-redirect=true");

    final List<LeaveRequest> persisted = verifyAdapter.findByApplicantEmpNum("E0001");
    assertThat(persisted).hasSize(1);
    final LeaveRequest saved = persisted.get(0);
    assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
    assertThat(saved.getLeaveType()).isEqualTo(LeaveType.PAID);
    assertThat(saved.getReason()).isEqualTo("私用");
    assertThat(saved.getAppliedAt()).isEqualTo(Instant.parse("2026-05-20T09:00:00Z"));
  }
}
