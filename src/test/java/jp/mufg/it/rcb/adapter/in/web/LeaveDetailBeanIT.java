package jp.mufg.it.rcb.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.logging.Logger;
import jp.mufg.it.rcb.adapter.out.persistence.LeaveRepositoryAdapter;
import jp.mufg.it.rcb.adapter.out.persistence.LeaveRequestMapper;
import jp.mufg.it.rcb.application.service.ApproveLeaveService;
import jp.mufg.it.rcb.application.service.FindLeaveService;
import jp.mufg.it.rcb.application.service.RejectLeaveService;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.application.service.support.UserInfoContextFactory;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import jp.mufg.it.rcb.domain.service.ApprovalPolicy;
import jp.mufg.it.rcb.shared.config.AppConfig;
import jp.mufg.it.rcb.shared.test.JpaTestSupport;
import jp.mufg.it.rcb.shared.test.ReflectionTestSupport;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

// 抑制理由:
//  - checkstyle:AbbreviationAsWordInName: failsafe 標準命名 (*IT) のため。
//  - PMD.ExcessiveImports: 3 種の Service（Find / Approve / Reject）+ 各 Command 経路を本物で動かす
//    結合テストの本質的責務上、import 数が増える。本クラス限定で許容する。
/**
 * {@link LeaveDetailBean} の結合テスト.
 *
 * <p>本物の {@link FindLeaveService} / {@link ApproveLeaveService} / {@link RejectLeaveService} と本物の
 * {@link LeaveRepositoryAdapter} を通じて H2 上の状態遷移を検証する。FacesContext は static mock。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings({"checkstyle:AbbreviationAsWordInName", "PMD.ExcessiveImports"})
class LeaveDetailBeanIT {

  /** H2 + Hibernate standalone bootstrap で生成する EntityManagerFactory. */
  private EntityManagerFactory emf;

  /** 各テストごとに払い出す EntityManager. */
  private EntityManager em;

  /** テスト対象（本物の Bean インスタンス）. */
  private LeaveDetailBean bean;

  /** seed 投入 / DB 検証用に共有する本物の Repository Adapter. */
  private LeaveRepositoryAdapter sharedAdapter;

  /** FacesContext.getCurrentInstance() を差し替える static mock. */
  private MockedStatic<FacesContext> facesCtxMock;

  /** FacesContext モックインスタンス（addMessage 呼び出しの検証用）. */
  private FacesContext mockFacesCtx;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeaveDetailBeanIT() {
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
            "jdbc:h2:mem:leave-detail-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__create_leave_request.sql");
  }

  /** EntityManagerFactory を閉じる. */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** 各テストの前に Bean → 本物 Service × 3 → 本物 Adapter → H2 の経路を組み立てる. */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    JpaTestSupport.runInTx(
        em, () -> em.createQuery("DELETE FROM LeaveRequestEntity").executeUpdate());

    sharedAdapter =
        JpaTestSupport.injectEntityManager(
            new LeaveRepositoryAdapter(new LeaveRequestMapper()), em);

    // 承認者 E0002 / ORG-001 / 部長層 M1.
    final UserInfoContext approverCtx =
        UserInfoContextFactory.create("E0002", "佐藤 花子", "ORG-001", "リテール開発部 第一課", "M1");
    final AppConfig appConfig = new AppConfig();
    ReflectionTestSupport.injectField(appConfig, "managerLayerCodesRaw", "M1,M2");
    final ApprovalPolicy approvalPolicy = new ApprovalPolicy();
    final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-05-21T10:00:00Z"));

    final FindLeaveService findService =
        new FindLeaveService(sharedAdapter, approverCtx, approvalPolicy, appConfig);
    final ApproveLeaveService approveService =
        new ApproveLeaveService(sharedAdapter, clock, approverCtx, approvalPolicy, appConfig);
    ReflectionTestSupport.injectField(approveService, "sysLogger", Logger.getLogger("test"));
    final RejectLeaveService rejectService =
        new RejectLeaveService(sharedAdapter, clock, approverCtx, approvalPolicy, appConfig);
    ReflectionTestSupport.injectField(rejectService, "sysLogger", Logger.getLogger("test"));

    bean = new LeaveDetailBean();
    bean.findLeave = findService;
    bean.approveLeave = approveService;
    bean.rejectLeave = rejectService;

    facesCtxMock = Mockito.mockStatic(FacesContext.class);
    mockFacesCtx = Mockito.mock(FacesContext.class);
    facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(mockFacesCtx);
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

  /** load() で H2 から詳細を取得し、申請者氏名等が detail にバインドされることを確認する. */
  @Test
  void loadFetchesDetailFromDatabase() {
    final Long id = seedPending();
    bean.setId(id);

    bean.load();

    assertThat(bean.getDetail()).isNotNull();
    assertThat(bean.getDetail().getApplicantEmpNum()).isEqualTo("E0001");
    assertThat(bean.getDetail().isCanApprove()).isTrue();
  }

  /** load() で id が null の場合は早期 return し detail も null のままであることを確認する. */
  @Test
  void loadReturnsEarlyWhenIdIsNull() {
    bean.setId(null);

    bean.load();

    assertThat(bean.getDetail()).isNull();
  }

  /** approve() で PENDING → APPROVED に状態遷移し、H2 にも反映されることを確認する. */
  @Test
  void approveTransitionsPendingToApproved() {
    final Long id = seedPending();
    bean.setId(id);
    bean.setJudgeComment("承認します");

    final String[] outcomeHolder = new String[1];
    JpaTestSupport.runInTx(em, () -> outcomeHolder[0] = bean.approve());

    assertThat(outcomeHolder[0]).isEqualTo("/leaves/list.xhtml?faces-redirect=true");
    em.clear();
    assertThat(sharedAdapter.findById(id))
        .get()
        .extracting(LeaveRequest::getStatus, LeaveRequest::getJudgeEmpNum)
        .containsExactly(LeaveStatus.APPROVED, "E0002");
  }

  /** reject() でコメント有りなら PENDING → REJECTED に状態遷移することを確認する. */
  @Test
  void rejectTransitionsPendingToRejectedWhenCommentProvided() {
    final Long id = seedPending();
    bean.setId(id);
    bean.setJudgeComment("根拠不足です");

    final String[] outcomeHolder = new String[1];
    JpaTestSupport.runInTx(em, () -> outcomeHolder[0] = bean.reject());

    assertThat(outcomeHolder[0]).isEqualTo("/leaves/list.xhtml?faces-redirect=true");
    em.clear();
    assertThat(sharedAdapter.findById(id))
        .get()
        .extracting(LeaveRequest::getStatus, LeaveRequest::getJudgeComment)
        .containsExactly(LeaveStatus.REJECTED, "根拠不足です");
  }

  /** reject() でコメント null の場合も FacesMessage 警告が出力され null が返ることを確認する. */
  @Test
  void rejectShowsWarningWhenCommentNull() {
    final Long id = seedPending();
    bean.setId(id);
    bean.setJudgeComment(null);

    final String outcome = bean.reject();

    assertThat(outcome).isNull();
    Mockito.verify(mockFacesCtx).addMessage(Mockito.eq(null), Mockito.any(FacesMessage.class));
  }

  /** reject() でコメント空文字の場合、FacesMessage 警告が出力され null が返ることを確認する. */
  @Test
  void rejectShowsWarningWhenCommentBlank() {
    final Long id = seedPending();
    bean.setId(id);
    bean.setJudgeComment("");

    final String outcome = bean.reject();

    assertThat(outcome).isNull();
    final ArgumentCaptor<FacesMessage> captor = ArgumentCaptor.forClass(FacesMessage.class);
    Mockito.verify(mockFacesCtx).addMessage(Mockito.eq(null), captor.capture());
    assertThat(captor.getValue().getSeverity()).isEqualTo(FacesMessage.SEVERITY_WARN);
    // ステータスが変わっていないこと.
    em.clear();
    assertThat(sharedAdapter.findById(id))
        .get()
        .extracting(LeaveRequest::getStatus)
        .isEqualTo(LeaveStatus.PENDING);
  }

  /**
   * E0001 申請者の PENDING を 1 件 seed し、生成された ID を返す.
   *
   * @return 生成された LeaveRequest の ID
   */
  private Long seedPending() {
    final LeaveRequest req =
        LeaveRequest.create(
            "E0001",
            "山田 太郎",
            "ORG-001",
            "リテール開発部 第一課",
            LeaveType.PAID,
            new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3)),
            "私用",
            Instant.parse("2026-05-20T09:00:00Z"));
    final LeaveRequest[] holder = new LeaveRequest[1];
    JpaTestSupport.runInTx(em, () -> holder[0] = sharedAdapter.save(req));
    return holder[0].getId();
  }
}
