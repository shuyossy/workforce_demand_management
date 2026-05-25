package jp.mufg.it.rcb.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** LeavePeriod 値オブジェクトの単体テスト. */
class LeavePeriodTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ LeavePeriodTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * start &lt;= end の正常系で、保持される start / end が正しいことを確認する.
   *
   * <p>1 つの構築結果に対する複数 assert は単一観点扱いとし、 PMD UnitTestContainsTooManyAsserts は個別抑制する。
   */
  @Test
  void startMustBeBeforeOrEqualEnd() {
    final LeavePeriod period = new LeavePeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));
    assertThat(period.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    assertThat(period.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 3));
  }

  @Test
  void singleDayPeriodIsAllowed() {
    final LeavePeriod period = new LeavePeriod(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));
    assertThat(period.getStartDate()).isEqualTo(period.getEndDate());
  }

  @Test
  void rejectsStartAfterEnd() {
    assertThatThrownBy(() -> new LeavePeriod(LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("start");
  }

  @Test
  void rejectsNullStartDate() {
    assertThatThrownBy(() -> new LeavePeriod(null, LocalDate.of(2026, 5, 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullEndDate() {
    assertThatThrownBy(() -> new LeavePeriod(LocalDate.of(2026, 5, 1), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
