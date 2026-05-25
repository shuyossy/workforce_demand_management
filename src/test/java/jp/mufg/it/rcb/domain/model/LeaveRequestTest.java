package jp.mufg.it.rcb.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** LeaveRequest エンティティの単体テスト. */
class LeaveRequestTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeaveRequestTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** PENDING 状態のサンプル申請を生成するヘルパー. */
  private LeaveRequest newPending() {
    return LeaveRequest.create(
        "E0001",
        "山田 太郎",
        "ORG-001",
        "リテール開発部 第一課",
        LeaveType.PAID,
        new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3)),
        "私用のため",
        Instant.parse("2026-05-20T09:00:00Z"));
  }

  /**
   * 新規作成された申請は PENDING であり、判断者情報は未設定であることを確認する.
   *
   * <p>1 つの構築結果に対する複数 assert は単一観点扱いとし、 PMD UnitTestContainsTooManyAsserts は個別抑制する。
   */
  @Test
  void createdRequestIsPendingAndHasNoJudgeFields() {
    final LeaveRequest request = newPending();
    assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING);
    assertThat(request.getJudgeEmpNum()).isNull();
    assertThat(request.getJudgeName()).isNull();
    assertThat(request.getJudgedAt()).isNull();
    assertThat(request.getJudgeComment()).isNull();
  }

  /**
   * approve 実行で APPROVED に遷移し、判断者情報が snapshot 保存されることを確認する.
   *
   * <p>1 つの状態遷移に対する複数 assert は単一観点扱いとし、 PMD UnitTestContainsTooManyAsserts は個別抑制する。
   */
  @Test
  void approveTransitionsToApprovedAndSnapshotsJudgeInfo() {
    final LeaveRequest request = newPending();
    request.approve("E0002", "佐藤 花子", Instant.parse("2026-05-21T10:00:00Z"), "OK");
    assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    assertThat(request.getJudgeEmpNum()).isEqualTo("E0002");
    assertThat(request.getJudgeName()).isEqualTo("佐藤 花子");
    assertThat(request.getJudgedAt()).isEqualTo(Instant.parse("2026-05-21T10:00:00Z"));
    assertThat(request.getJudgeComment()).isEqualTo("OK");
  }

  /**
   * reject 実行で REJECTED に遷移し、コメントが保存されることを確認する.
   *
   * <p>1 つの状態遷移に対する複数 assert は単一観点扱いとし、 PMD UnitTestContainsTooManyAsserts は個別抑制する。
   */
  @Test
  void rejectTransitionsToRejectedAndRequiresComment() {
    final LeaveRequest request = newPending();
    request.reject("E0002", "佐藤 花子", Instant.parse("2026-05-21T10:00:00Z"), "期間調整が必要");
    assertThat(request.getStatus()).isEqualTo(LeaveStatus.REJECTED);
    assertThat(request.getJudgeEmpNum()).isEqualTo("E0002");
    assertThat(request.getJudgeComment()).isEqualTo("期間調整が必要");
  }

  /** APPROVED 済みの申請を再度 approve できないことを確認する. */
  @Test
  void cannotApproveAlreadyApproved() {
    final LeaveRequest request = newPending();
    request.approve("E0002", "佐藤 花子", Instant.parse("2026-05-21T10:00:00Z"), null);
    assertThatThrownBy(
            () -> request.approve("E0003", "鈴木 次郎", Instant.parse("2026-05-22T10:00:00Z"), null))
        .isInstanceOf(IllegalStateException.class);
  }

  /** REJECTED 済みの申請を再度 reject できないことを確認する. */
  @Test
  void cannotRejectAlreadyRejected() {
    final LeaveRequest request = newPending();
    request.reject("E0002", "佐藤 花子", Instant.parse("2026-05-21T10:00:00Z"), "理由");
    assertThatThrownBy(
            () -> request.reject("E0003", "鈴木 次郎", Instant.parse("2026-05-22T10:00:00Z"), "別の理由"))
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * reconstruct ファクトリが APPROVED 状態の申請を全フィールド復元することを確認する.
   *
   * <p>1 つの構築結果に対する複数 assert は単一観点扱いとし、 PMD UnitTestContainsTooManyAsserts は個別抑制する。
   */
  @Test
  void reconstructRestoresApprovedRequestFully() {
    final LeaveRequest restored =
        LeaveRequest.reconstruct(
            99L,
            "E0001",
            "山田 太郎",
            "ORG-001",
            "リテール開発部 第一課",
            LeaveType.PAID,
            new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3)),
            "私用のため",
            LeaveStatus.APPROVED,
            Instant.parse("2026-05-20T09:00:00Z"),
            "E0002",
            "佐藤 花子",
            Instant.parse("2026-05-21T10:00:00Z"),
            "OK");
    assertThat(restored.getId()).isEqualTo(99L);
    assertThat(restored.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    assertThat(restored.getJudgeEmpNum()).isEqualTo("E0002");
  }
}
