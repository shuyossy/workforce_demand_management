package jp.mufg.it.rcb.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link DevUser#getDisplayLabel()} の整形ロジック単体テスト. */
class DevUserTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ DevUserTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  @Test
  void buildsDisplayLabelWithJapaneseDelimiters() {
    final DevUser user = new DevUser("E0001", "山田 太郎", "ORG-001", "リテール開発部 第一課", "L1");
    assertThat(user.getDisplayLabel()).isEqualTo("E0001 - 山田 太郎（リテール開発部 第一課 / L1）");
  }

  @Test
  void preservesManagerLayerCode() {
    final DevUser user = new DevUser("E0002", "佐藤 花子", "ORG-001", "リテール開発部 第一課", "M1");
    assertThat(user.getDisplayLabel()).isEqualTo("E0002 - 佐藤 花子（リテール開発部 第一課 / M1）");
  }
}
