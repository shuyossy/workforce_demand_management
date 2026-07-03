package jp.mufg.it.rcb.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link TaskStatus} の単体テスト. */
class TaskStatusTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ TaskStatusTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** TODO は完了可能. */
  @Test
  void todoCanComplete() {
    assertThat(TaskStatus.TODO.canComplete()).isTrue();
  }

  /** DONE は完了不可（再完了不可）. */
  @Test
  void doneCannotComplete() {
    assertThat(TaskStatus.DONE.canComplete()).isFalse();
  }
}
