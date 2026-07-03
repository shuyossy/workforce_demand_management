package jp.mufg.it.rcb.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** {@link CreateTaskCommand} の Bean Validation 単体テスト. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreateTaskCommandTest {

  /** Validator ファクトリ. */
  private ValidatorFactory factory;

  /** 検証対象 Validator. */
  private Validator validator;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ CreateTaskCommandTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** Validator を初期化する. */
  @BeforeAll
  void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  /** ValidatorFactory を閉じる. */
  @AfterAll
  void tearDown() {
    if (factory != null) {
      factory.close();
    }
  }

  /** 正常なタイトルは違反なし. */
  @Test
  void validTitlePasses() {
    final CreateTaskCommand cmd = CreateTaskCommand.builder().title("買い物").build();
    assertThat(validator.validate(cmd)).isEmpty();
  }

  /** 空白タイトルは @NotBlank 違反. */
  @Test
  void blankTitleFails() {
    final CreateTaskCommand cmd = CreateTaskCommand.builder().title("  ").build();
    assertThat(validator.validate(cmd)).isNotEmpty();
  }

  /** null タイトルは @NotBlank 違反. */
  @Test
  void nullTitleFails() {
    final CreateTaskCommand cmd = CreateTaskCommand.builder().title(null).build();
    assertThat(validator.validate(cmd)).isNotEmpty();
  }

  /** 100 文字は許容、101 文字は @Size 違反. */
  @Test
  void titleLengthBoundary() {
    final CreateTaskCommand ok = CreateTaskCommand.builder().title("あ".repeat(100)).build();
    final CreateTaskCommand ng = CreateTaskCommand.builder().title("あ".repeat(101)).build();
    assertThat(validator.validate(ok)).isEmpty();
    assertThat(validator.validate(ng)).isNotEmpty();
  }
}
