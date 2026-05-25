package jp.mufg.it.rcb.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** LeaveStatus enum の単体テスト. */
class LeaveStatusTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeaveStatusTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** values() が全定数を宣言順で返すことを確認する. */
  @Test
  void valuesContainsAllConstants() {
    assertThat(LeaveStatus.values())
        .containsExactly(LeaveStatus.PENDING, LeaveStatus.APPROVED, LeaveStatus.REJECTED);
  }

  /** valueOf(name) が対応する定数を返すことを確認する. */
  @Test
  void valueOfResolvesByName() {
    assertThat(LeaveStatus.valueOf("PENDING")).isEqualTo(LeaveStatus.PENDING);
  }

  /** PENDING から APPROVED への遷移が許可されることを確認する. */
  @Test
  void canTransitionFromPendingToApproved() {
    assertThat(LeaveStatus.PENDING.canTransitionTo(LeaveStatus.APPROVED)).isTrue();
  }

  /** PENDING から REJECTED への遷移が許可されることを確認する. */
  @Test
  void canTransitionFromPendingToRejected() {
    assertThat(LeaveStatus.PENDING.canTransitionTo(LeaveStatus.REJECTED)).isTrue();
  }

  /** APPROVED からの遷移は禁止されることを確認する. */
  @Test
  void cannotTransitionFromApproved() {
    assertThat(LeaveStatus.APPROVED.canTransitionTo(LeaveStatus.PENDING)).isFalse();
  }

  /** REJECTED からの遷移は禁止されることを確認する. */
  @Test
  void cannotTransitionFromRejected() {
    assertThat(LeaveStatus.REJECTED.canTransitionTo(LeaveStatus.APPROVED)).isFalse();
  }

  /** PENDING から PENDING への自己遷移は禁止されることを確認する. */
  @Test
  void cannotTransitionFromPendingToPending() {
    assertThat(LeaveStatus.PENDING.canTransitionTo(LeaveStatus.PENDING)).isFalse();
  }
}
