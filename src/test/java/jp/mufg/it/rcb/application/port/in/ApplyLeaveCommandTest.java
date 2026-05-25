package jp.mufg.it.rcb.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import jp.mufg.it.rcb.domain.model.LeaveType;
import org.junit.jupiter.api.Test;

/**
 * {@link ApplyLeaveCommand#isValidPeriod()} の {@code @AssertTrue} ロジック単体テスト.
 *
 * <p>Bean Validation 連携は別途結合層で検証する。ここでは判定式の分岐網羅のみを目的とする。
 */
class ApplyLeaveCommandTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ ApplyLeaveCommandTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  @Test
  void isValidPeriodReturnsTrueWhenStartBeforeEnd() {
    final ApplyLeaveCommand cmd =
        ApplyLeaveCommand.builder()
            .leaveType(LeaveType.PAID)
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(LocalDate.of(2026, 6, 3))
            .reason("私用のため")
            .build();
    assertThat(cmd.isValidPeriod()).isTrue();
  }

  @Test
  void isValidPeriodReturnsTrueWhenStartEqualsEnd() {
    final ApplyLeaveCommand cmd =
        ApplyLeaveCommand.builder()
            .leaveType(LeaveType.PAID)
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(LocalDate.of(2026, 6, 1))
            .reason("半休")
            .build();
    assertThat(cmd.isValidPeriod()).isTrue();
  }

  @Test
  void isValidPeriodReturnsFalseWhenStartAfterEnd() {
    final ApplyLeaveCommand cmd =
        ApplyLeaveCommand.builder()
            .leaveType(LeaveType.PAID)
            .startDate(LocalDate.of(2026, 6, 5))
            .endDate(LocalDate.of(2026, 6, 1))
            .reason("逆転")
            .build();
    assertThat(cmd.isValidPeriod()).isFalse();
  }

  @Test
  void isValidPeriodReturnsTrueWhenStartIsNull() {
    final ApplyLeaveCommand cmd =
        ApplyLeaveCommand.builder()
            .leaveType(LeaveType.PAID)
            .startDate(null)
            .endDate(LocalDate.of(2026, 6, 1))
            .reason("null start")
            .build();
    assertThat(cmd.isValidPeriod()).isTrue();
  }

  @Test
  void isValidPeriodReturnsTrueWhenEndIsNull() {
    final ApplyLeaveCommand cmd =
        ApplyLeaveCommand.builder()
            .leaveType(LeaveType.PAID)
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(null)
            .reason("null end")
            .build();
    assertThat(cmd.isValidPeriod()).isTrue();
  }
}
