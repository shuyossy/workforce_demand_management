package jp.mufg.it.rcb.application.port.out;

import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.domain.model.Task;

/** タスクの永続化を担う出力 port. */
public interface TaskRepositoryPort {

  /**
   * タスクを永続化する.
   *
   * @param task 永続化対象（新規時は ID 未採番、更新時は採番済み）
   * @return 採番済み（または更新済み）のタスク
   */
  Task save(Task task);

  /**
   * ID を指定してタスクを取得する.
   *
   * @param taskId タスク ID
   * @return 該当するタスク（存在しない場合は空）
   */
  Optional<Task> findById(long taskId);

  /**
   * 全タスクを作成日時の降順で取得する.
   *
   * @return タスクの一覧（新しい順）
   */
  List<Task> findAllOrderByCreatedAtDesc();
}
