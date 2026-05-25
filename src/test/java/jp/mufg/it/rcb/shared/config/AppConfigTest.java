package jp.mufg.it.rcb.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * {@link AppConfig#getManagerLayerCodes()} のカンマ区切り解析ロジック単体テスト.
 *
 * <p>MicroProfile Config 経由の {@code @ConfigProperty} 解決は CDI コンテナ依存のため、 package-private な {@code
 * managerLayerCodesRaw} をリフレクションで直接差し込み、純粋な文字列パース挙動のみを検証する。
 */
class AppConfigTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ AppConfigTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * リフレクションで package-private フィールド {@code managerLayerCodesRaw} に値を設定する.
   *
   * @param config 対象 {@link AppConfig}
   * @param value 設定値（null 可）
   */
  private static void setRaw(final AppConfig config, final String value) {
    try {
      final Field field = AppConfig.class.getDeclaredField("managerLayerCodesRaw");
      field.setAccessible(true);
      field.set(config, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("リフレクション設定に失敗", e);
    }
  }

  @Test
  void returnsEmptySetWhenRawIsNull() {
    final AppConfig config = new AppConfig();
    setRaw(config, null);
    assertThat(config.getManagerLayerCodes()).isEmpty();
  }

  @Test
  void returnsEmptySetWhenRawIsBlank() {
    final AppConfig config = new AppConfig();
    setRaw(config, "   ");
    assertThat(config.getManagerLayerCodes()).isEmpty();
  }

  @Test
  void parsesCommaSeparatedCodes() {
    final AppConfig config = new AppConfig();
    setRaw(config, "M1,M2");
    assertThat(config.getManagerLayerCodes()).containsExactlyInAnyOrder("M1", "M2");
  }

  @Test
  void parsesCommaSeparatedCodesWithSurroundingWhitespace() {
    final AppConfig config = new AppConfig();
    setRaw(config, "M1 , M2 ,  M3");
    assertThat(config.getManagerLayerCodes()).containsExactlyInAnyOrder("M1", "M2", "M3");
  }

  @Test
  void parsesSingleCode() {
    final AppConfig config = new AppConfig();
    setRaw(config, "M1");
    assertThat(config.getManagerLayerCodes()).containsExactly("M1");
  }
}
