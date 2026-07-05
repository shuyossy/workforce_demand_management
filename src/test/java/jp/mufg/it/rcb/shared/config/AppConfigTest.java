package jp.mufg.it.rcb.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link AppConfig} の単体テスト.
 *
 * <p>現時点では公開する設定値がない骨格 Bean のため、CDI 既定コンストラクタが正常にインスタンス化できることのみを確認する。 今後 {@code app.*} 設定を追加する際は、
 * 追加するメソッドに対応する単体テストを本クラスに追加すること。
 */
class AppConfigTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ AppConfigTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** デフォルトコンストラクタでインスタンス化できる. */
  @Test
  void canBeInstantiated() {
    assertThat(new AppConfig()).isNotNull();
  }
}
