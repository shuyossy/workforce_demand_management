package jp.mufg.it.rcb.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** LeaveType enum の単体テスト. */
class LeaveTypeTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeaveTypeTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** values() が全定数を宣言順で返すことを確認する. */
  @Test
  void valuesContainsAllConstants() {
    assertThat(LeaveType.values())
        .containsExactly(LeaveType.PAID, LeaveType.SPECIAL, LeaveType.COMPENSATORY);
  }

  /** valueOf(name) が対応する定数を返すことを確認する. */
  @Test
  void valueOfResolvesByName() {
    assertThat(LeaveType.valueOf("PAID")).isEqualTo(LeaveType.PAID);
  }
}
