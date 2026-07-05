package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import jp.mufg.it.rcb.application.port.in.ListTasksUseCase;
import jp.mufg.it.rcb.application.port.in.TaskSummary;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;

/** タスク一覧ユースケースの実装（読み取り専用で全件を作成日時降順に取得）. */
@ApplicationScoped
public class ListTasksService implements ListTasksUseCase {

  /** 永続化 port. */
  private final TaskRepositoryPort repository;

  /**
   * 依存を注入する.
   *
   * @param repository 永続化 port
   */
  @Inject
  public ListTasksService(final TaskRepositoryPort repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public List<TaskSummary> list() {
    return repository.findAllOrderByCreatedAtDesc().stream().map(this::toSummary).toList();
  }

  /**
   * Task を表示用 DTO に変換する.
   *
   * @param task 変換対象のタスク
   * @return 表示用 DTO
   */
  private TaskSummary toSummary(final Task task) {
    return TaskSummary.builder()
        .id(task.getId())
        .title(task.getTitle())
        .status(task.getStatus())
        .createdAt(task.getCreatedAt())
        .completedAt(task.getCompletedAt())
        .build();
  }
}
