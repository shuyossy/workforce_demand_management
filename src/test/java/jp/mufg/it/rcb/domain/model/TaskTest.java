package jp.mufg.it.rcb.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** {@link Task} の単体テスト. */
class TaskTest {

  /** 生成基準時刻. */
  private static final Instant NOW = Instant.parse("2026-07-03T09:00:00Z");

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ TaskTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** create() は TODO / completedAt=null で生成する. */
  @Test
  void createStartsAsTodo() {
    final Task task = Task.create("買い物", NOW);
    assertThat(task.getId()).isNull();
    assertThat(task.getTitle()).isEqualTo("買い物");
    assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(task.getCreatedAt()).isEqualTo(NOW);
    assertThat(task.getCompletedAt()).isNull();
  }

  /** complete() は TODO→DONE に遷移し completedAt を設定する. */
  @Test
  void completeTransitionsToDone() {
    final Task task = Task.create("買い物", NOW);
    final Instant completedAt = Instant.parse("2026-07-03T10:00:00Z");
    task.complete(completedAt);
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(completedAt);
  }

  /** 既に DONE のタスクを complete() すると IllegalStateException（設計バグ防御）. */
  @Test
  void completeOnDoneThrows() {
    final Task task = Task.create("買い物", NOW);
    task.complete(Instant.parse("2026-07-03T10:00:00Z"));
    assertThatThrownBy(() -> task.complete(Instant.parse("2026-07-03T11:00:00Z")))
        .isInstanceOf(IllegalStateException.class);
  }

  /** title が空文字だと生成できない（値オブジェクト境界での不変条件）. */
  @Test
  void createRejectsBlankTitle() {
    assertThatThrownBy(() -> Task.create("  ", NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  /** reconstruct() は永続層の値をそのまま復元する. */
  @Test
  void reconstructRestoresState() {
    final Instant completedAt = Instant.parse("2026-07-03T10:00:00Z");
    final Task task = Task.reconstruct(5L, "会議", TaskStatus.DONE, NOW, completedAt);
    assertThat(task.getId()).isEqualTo(5L);
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(completedAt);
  }
}
