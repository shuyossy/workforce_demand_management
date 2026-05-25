package jp.mufg.it.rcb.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.faces.context.FacesContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import jp.mufg.it.rcb.adapter.out.persistence.LeaveRepositoryAdapter;
import jp.mufg.it.rcb.adapter.out.persistence.LeaveRequestMapper;
import jp.mufg.it.rcb.application.port.in.LeaveRequestSummary;
import jp.mufg.it.rcb.application.service.ListLeavesService;
import jp.mufg.it.rcb.application.service.support.UserInfoContextFactory;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import jp.mufg.it.rcb.shared.test.JpaTestSupport;
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
//    標準的な命名規約。AGENTS.md / rules/README.md の方針上、failsafe 標準命名を優先する。
/**
 * {@link LeaveListBean} の結合テスト.
 *
 * <p>本物の {@link ListLeavesService} と本物の {@link LeaveRepositoryAdapter} 経由で H2 まで実際に問い合わせ、 MINE /
 * APPROVABLE 双方の一覧取得経路を検証する。FacesContext は static mock。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class LeaveListBeanIT {

  /** H2 + Hibernate standalone bootstrap で生成する EntityManagerFactory. */
  private EntityManagerFactory emf;

  /** 各テストごとに払い出す EntityManager. */
  private EntityManager em;

  /** テスト対象（本物の Bean インスタンス）. */
  private LeaveListBean bean;

  /** seed 投入用に共有する本物の Repository Adapter. */
  private LeaveRepositoryAdapter seedAdapter;

  /** FacesContext.getCurrentInstance() を差し替える static mock. */
  private MockedStatic<FacesContext> facesCtxMock;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeaveListBeanIT() {
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
            "jdbc:h2:mem:leave-list-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__create_leave_request.sql");
  }

  /** EntityManagerFactory を閉じる. */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** 各テストの前にテーブルを空にし、Bean → 本物 Service → 本物 Adapter → H2 の経路を組み立てる. */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    JpaTestSupport.runInTx(
        em, () -> em.createQuery("DELETE FROM LeaveRequestEntity").executeUpdate());

    final LeaveRepositoryAdapter realAdapter =
        JpaTestSupport.injectEntityManager(
            new LeaveRepositoryAdapter(new LeaveRequestMapper()), em);
    seedAdapter = realAdapter;

    final UserInfoContext userCtx =
        UserInfoContextFactory.create("E0001", "山田 太郎", "ORG-001", "リテール開発部 第一課", "S1");
    final ListLeavesService realService = new ListLeavesService(realAdapter, userCtx);

    bean = new LeaveListBean();
    bean.listLeaves = realService;

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

  /** {@code @PostConstruct init()} で MINE / APPROVABLE 双方が取得されることを確認する. */
  @Test
  void initLoadsMineAndApprovableLists() {
    seed("E0001", LeaveStatus.PENDING, "ORG-001"); // 自分 PENDING
    seed("E0001", LeaveStatus.APPROVED, "ORG-001"); // 自分 APPROVED
    seed("E0002", LeaveStatus.PENDING, "ORG-001"); // 同部署他者 PENDING → APPROVABLE
    seed("E0003", LeaveStatus.PENDING, "ORG-002"); // 別部署 PENDING → APPROVABLE 対象外

    bean.init();

    assertThat(bean.getMyLeaves()).hasSize(2);
    assertThat(bean.getMyLeaves())
        .extracting(LeaveRequestSummary::getApplicantEmpNum)
        .containsOnly("E0001");

    assertThat(bean.getApprovableLeaves()).hasSize(1);
    assertThat(bean.getApprovableLeaves().get(0).getApplicantEmpNum()).isEqualTo("E0002");
  }

  /** {@code refresh()} で再取得すると seed 追加分が反映されることを確認する. */
  @Test
  void refreshReflectsLatestSeed() {
    bean.init();
    assertThat(bean.getMyLeaves()).isEmpty();

    seed("E0001", LeaveStatus.PENDING, "ORG-001");

    bean.refresh();

    assertThat(bean.getMyLeaves()).hasSize(1);
  }

  /**
   * H2 にシードを 1 件投入する.
   *
   * @param empNum 申請者社員番号
   * @param status 申請ステータス
   * @param orgId 所属組織 ID
   */
  private void seed(final String empNum, final LeaveStatus status, final String orgId) {
    final LeaveRequest req =
        LeaveRequest.reconstruct(
            null,
            empNum,
            "name",
            orgId,
            "Org Name",
            LeaveType.PAID,
            new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
            "理由",
            status,
            Instant.parse("2026-05-20T09:00:00Z"),
            status == LeaveStatus.PENDING ? null : "E0099",
            status == LeaveStatus.PENDING ? null : "X",
            status == LeaveStatus.PENDING ? null : Instant.parse("2026-05-21T10:00:00Z"),
            status == LeaveStatus.PENDING ? null : "comment");
    JpaTestSupport.runInTx(em, () -> seedAdapter.save(req));
  }
}
