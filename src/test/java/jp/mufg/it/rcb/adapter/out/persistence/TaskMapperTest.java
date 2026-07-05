package jp.mufg.it.rcb.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

/** {@link TaskMapper} の単体テスト. */
class TaskMapperTest {

  /** テスト対象. */
  private final TaskMapper mapper = new TaskMapper();

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ TaskMapperTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** ドメイン→Entity 変換で全項目が転写される（未完了 = completedAt null）. */
  @Test
  void copiesAllFieldsToEntity() {
    final Task task =
        Task.reconstruct(1L, "買い物", TaskStatus.TODO, Instant.parse("2026-07-03T09:00:00Z"), null);
    final TaskEntity entity = mapper.toEntity(task);
    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getTitle()).isEqualTo("買い物");
    assertThat(entity.getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(entity.getCreatedAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
    assertThat(entity.getCompletedAt()).isNull();
  }

  /** Entity→ドメイン復元で完了日時付き（DONE）が復元される. */
  @Test
  void restoresDoneTaskFromEntity() {
    final TaskEntity entity = new TaskEntity();
    entity.setId(2L);
    entity.setTitle("会議");
    entity.setStatus(TaskStatus.DONE);
    entity.setCreatedAt(Instant.parse("2026-07-03T09:00:00Z"));
    entity.setCompletedAt(Instant.parse("2026-07-03T10:00:00Z"));

    final Task task = mapper.toDomain(entity);
    assertThat(task.getId()).isEqualTo(2L);
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
  }
}
