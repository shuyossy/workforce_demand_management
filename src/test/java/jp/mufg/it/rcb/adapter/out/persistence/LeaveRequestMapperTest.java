package jp.mufg.it.rcb.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import org.junit.jupiter.api.Test;

/** LeaveRequestMapper の単体テスト. */
class LeaveRequestMapperTest {

  /** テスト対象の Mapper（POJO のため new で直接生成）. */
  private final LeaveRequestMapper mapper = new LeaveRequestMapper();

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeaveRequestMapperTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * PENDING 状態の申請が toEntity → toDomain で往復しても主要フィールドが保持されることを確認する.
   *
   * <p>1 つの往復変換に対する複数 assert は単一観点扱いとし、 PMD UnitTestContainsTooManyAsserts は個別抑制する。
   */
  @Test
  void roundTripsPendingRequest() {
    final LeaveRequest domain =
        LeaveRequest.reconstruct(
            5L,
            "E0001",
            "山田 太郎",
            "ORG-001",
            "リテール開発部 第一課",
            LeaveType.PAID,
            new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3)),
            "私用",
            LeaveStatus.PENDING,
            Instant.parse("2026-05-20T09:00:00Z"),
            null,
            null,
            null,
            null);

    final LeaveRequestEntity entity = mapper.toEntity(domain);
    assertThat(entity.getId()).isEqualTo(5L);
    assertThat(entity.getStatus()).isEqualTo(LeaveStatus.PENDING);
    assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));

    final LeaveRequest restored = mapper.toDomain(entity);
    assertThat(restored.getId()).isEqualTo(5L);
    assertThat(restored.getPeriod().getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(restored.getPeriod().getEndDate()).isEqualTo(LocalDate.of(2026, 6, 3));
    assertThat(restored.getStatus()).isEqualTo(LeaveStatus.PENDING);
  }

  /**
   * APPROVED 状態の申請が toEntity → toDomain で往復しても判断者フィールドが保持されることを確認する.
   *
   * <p>1 つの往復変換に対する複数 assert は単一観点扱いとし、 PMD UnitTestContainsTooManyAsserts は個別抑制する。
   */
  @Test
  void roundTripsApprovedRequestWithJudgeFields() {
    final LeaveRequest domain =
        LeaveRequest.reconstruct(
            6L,
            "E0001",
            "山田 太郎",
            "ORG-001",
            "リテール開発部 第一課",
            LeaveType.SPECIAL,
            new LeavePeriod(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10)),
            "家族行事",
            LeaveStatus.APPROVED,
            Instant.parse("2026-05-21T09:00:00Z"),
            "E0002",
            "佐藤 花子",
            Instant.parse("2026-05-22T10:00:00Z"),
            "OK");
    final LeaveRequestEntity entity = mapper.toEntity(domain);
    final LeaveRequest restored = mapper.toDomain(entity);
    assertThat(restored.getJudgeEmpNum()).isEqualTo("E0002");
    assertThat(restored.getJudgeName()).isEqualTo("佐藤 花子");
    assertThat(restored.getJudgedAt()).isEqualTo(Instant.parse("2026-05-22T10:00:00Z"));
    assertThat(restored.getJudgeComment()).isEqualTo("OK");
  }

  /**
   * applyToEntity が既存 Entity のフィールドをドメインの値で更新することを確認する.
   *
   * <p>1 つの更新操作に対する複数 assert は単一観点扱いとし、 PMD UnitTestContainsTooManyAsserts は個別抑制する。
   */
  @Test
  void updatesExistingEntityInPlace() {
    final LeaveRequestEntity existing = new LeaveRequestEntity();
    existing.setId(7L);
    existing.setStatus(LeaveStatus.PENDING);

    final LeaveRequest approved =
        LeaveRequest.reconstruct(
            7L,
            "E0001",
            "山田 太郎",
            "ORG-001",
            "リテール開発部 第一課",
            LeaveType.PAID,
            new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
            "私用",
            LeaveStatus.APPROVED,
            Instant.parse("2026-05-20T09:00:00Z"),
            "E0002",
            "佐藤 花子",
            Instant.parse("2026-05-21T10:00:00Z"),
            null);

    mapper.applyToEntity(approved, existing);
    assertThat(existing.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    assertThat(existing.getJudgeEmpNum()).isEqualTo("E0002");
  }
}
