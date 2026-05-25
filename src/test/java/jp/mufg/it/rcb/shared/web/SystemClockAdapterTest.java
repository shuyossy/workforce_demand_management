package jp.mufg.it.rcb.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * {@link SystemClockAdapter#now()} の単体テスト.
 *
 * <p>システム時計を直接呼ぶ実装なので、テスト時刻との差が十分小さい（5 秒以内）ことを確認する。
 */
class SystemClockAdapterTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ SystemClockAdapterTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** 1 観測（now() の呼び出し）に対する戻り値の性質を 3 連 assert で網羅する. */
  @Test
  void nowReturnsNonNullInstantCloseToSystemTime() {
    final SystemClockAdapter adapter = new SystemClockAdapter();
    final Instant before = Instant.now();
    final Instant actual = adapter.now();
    final Instant after = Instant.now();

    assertThat(actual).isNotNull();
    assertThat(Duration.between(before, after))
        .as("システム時計を介した now() 呼び出しのリードタイム")
        .isLessThan(Duration.ofSeconds(5));
    assertThat(actual).isBetween(before, after);
  }
}
