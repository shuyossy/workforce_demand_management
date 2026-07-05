package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import jp.mufg.it.rcb.application.port.in.TaskSummary;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ListTasksService} の単体テスト. */
@ExtendWith(MockitoExtension.class)
class ListTasksServiceTest {

  /** 永続化 port のモック. */
  @Mock private TaskRepositoryPort repository;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ ListTasksServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** repository の Task を TaskSummary に変換して返す. */
  @Test
  void mapsTasksToSummaries() {
    final Task task =
        Task.reconstruct(1L, "買い物", TaskStatus.TODO, Instant.parse("2026-07-03T09:00:00Z"), null);
    when(repository.findAllOrderByCreatedAtDesc()).thenReturn(List.of(task));

    final List<TaskSummary> result = new ListTasksService(repository).list();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(1L);
    assertThat(result.get(0).getTitle()).isEqualTo("買い物");
    assertThat(result.get(0).getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(result.get(0).getCreatedAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
    assertThat(result.get(0).getCompletedAt()).isNull();
  }

  /** タスクが無い場合は空リストを返す. */
  @Test
  void returnsEmptyWhenNoTasks() {
    when(repository.findAllOrderByCreatedAtDesc()).thenReturn(List.of());
    assertThat(new ListTasksService(repository).list()).isEmpty();
  }
}
