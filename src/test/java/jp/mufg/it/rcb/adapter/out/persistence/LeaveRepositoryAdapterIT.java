package jp.mufg.it.rcb.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import jp.mufg.it.rcb.shared.test.JpaTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

// 抑制理由:
//  - checkstyle:AbbreviationAsWordInName: クラス名末尾の "IT" は maven-failsafe が結合テストを識別する
//    標準的な命名規約（surefire は *Test、failsafe は *IT）であり、Google Java Style の連続
//    大文字数 1 までという規則と構造的に衝突する。AGENTS.md / rules/README.md の方針上、
//    failsafe 標準命名を優先し、本クラス限定で抑制する。
/**
 * {@link LeaveRepositoryAdapter} の H2 結合テスト.
 *
 * <p>H2 インメモリ + Hibernate standalone bootstrap で JPA 経路を検証する。本物の Adapter インスタンスに {@link
 * JpaTestSupport#injectEntityManager(Object, EntityManager)} で EntityManager
 * を差し込み、container-managed 注入と等価な状態で JPQL を実際に走らせる。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class LeaveRepositoryAdapterIT {

  /** Hibernate standalone bootstrap で生成する EntityManagerFactory（PER_CLASS で 1 度だけ生成）. */
  private EntityManagerFactory emf;

  /** 各テストごとに払い出す EntityManager. */
  private EntityManager em;

  /** テスト対象（本物の LeaveRepositoryAdapter に EM をリフレクション注入した実体）. */
  private LeaveRepositoryPort repository;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeaveRepositoryAdapterIT() {
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
            "jdbc:h2:mem:app-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__create_leave_request.sql");
  }

  /** EntityManagerFactory を閉じる. */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** 各テストの前に EntityManager を払い出し、テーブルを空にしてから本物 Adapter に EM を注入する. */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    repository =
        JpaTestSupport.injectEntityManager(
            new LeaveRepositoryAdapter(new LeaveRequestMapper()), em);
    JpaTestSupport.runInTx(
        em, () -> em.createQuery("DELETE FROM LeaveRequestEntity").executeUpdate());
  }

  /** save → findById で 1 件取得し、申請者社員番号が保持されていることを確認する. */
  @Test
  void savesAndFindsById() {
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

    final LeaveRequest[] savedHolder = new LeaveRequest[1];
    JpaTestSupport.runInTx(em, () -> savedHolder[0] = repository.save(req));
    assertThat(savedHolder[0].getId()).isNotNull();

    final Optional<LeaveRequest> found = repository.findById(savedHolder[0].getId());
    assertThat(found).isPresent();
    assertThat(found.get().getApplicantEmpNum()).isEqualTo("E0001");
  }

  /** findByApplicantEmpNum が指定社員番号の申請のみ返すことを確認する. */
  @Test
  void findByApplicantEmpNum() {
    seed("E0001", LeaveStatus.PENDING);
    seed("E0001", LeaveStatus.APPROVED);
    seed("E0002", LeaveStatus.PENDING);

    final List<LeaveRequest> mine = repository.findByApplicantEmpNum("E0001");
    assertThat(mine).hasSize(2).allMatch(r -> "E0001".equals(r.getApplicantEmpNum()));
  }

  /** save が id != null の場合は既存 Entity に値を反映して update する経路を確認する. */
  @Test
  void updatesExistingEntityWhenSavedWithId() {
    seed("E0001", LeaveStatus.PENDING);
    final List<LeaveRequest> mine = repository.findByApplicantEmpNum("E0001");
    final LeaveRequest existing = mine.get(0);

    existing.approve("E0002", "佐藤 花子", Instant.parse("2026-05-21T10:00:00Z"), "OK");
    JpaTestSupport.runInTx(em, () -> repository.save(existing));

    final Optional<LeaveRequest> found = repository.findById(existing.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(LeaveStatus.APPROVED);
    assertThat(found.get().getJudgeComment()).isEqualTo("OK");
  }

  /** save が id != null かつ Entity が存在しない場合は IllegalStateException を投げることを確認する. */
  @Test
  void savesThrowsWhenEntityNotFoundForGivenId() {
    final LeaveRequest unsaved =
        LeaveRequest.reconstruct(
            9999L,
            "E0001",
            "山田 太郎",
            "ORG-001",
            "リテール開発部 第一課",
            LeaveType.PAID,
            new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
            "理由",
            LeaveStatus.PENDING,
            Instant.parse("2026-05-20T09:00:00Z"),
            null,
            null,
            null,
            null);

    assertThatThrownBy(() -> JpaTestSupport.runInTx(em, () -> repository.save(unsaved)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Entity not found for id=9999");
  }

  /** findApprovablePending が指定部署 / PENDING / 除外社員番号フィルタを正しく適用することを確認する. */
  @Test
  void findApprovablePending() {
    // 同じ部署 (ORG-001) で PENDING の他者申請 → 承認対象
    seed("E0001", LeaveStatus.PENDING);
    // 同じ部署だが APPROVED → 承認対象外
    seed("E0001", LeaveStatus.APPROVED);
    // 別部署 (ORG-002) で PENDING → 承認対象外
    seed("E0003", LeaveStatus.PENDING, "ORG-002");

    final List<LeaveRequest> approvable = repository.findApprovablePending("ORG-001", "E0002");
    assertThat(approvable).hasSize(1);
    assertThat(approvable.get(0).getApplicantEmpNum()).isEqualTo("E0001");
  }

  /**
   * 既定 ORG-001 でシードを 1 件投入する.
   *
   * @param empNum 申請者社員番号
   * @param status 申請ステータス
   */
  private void seed(final String empNum, final LeaveStatus status) {
    seed(empNum, status, "ORG-001");
  }

  /**
   * 指定された属性でシードを 1 件投入する（assert は呼び出し側のテストで行う）.
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
    JpaTestSupport.runInTx(em, () -> repository.save(req));
  }
}
