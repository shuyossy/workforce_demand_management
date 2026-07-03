package jp.mufg.it.rcb.application.port.in;

import java.io.Serializable;
import java.time.Instant;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * タスク一覧表示用 DTO.
 *
 * <p>{@code @ViewScoped} バッキングビーン（TaskListBean）のフィールドとして保持されるため Serializable。
 */
@Getter
@Builder
@AllArgsConstructor
public class TaskSummary implements Serializable {

  private static final long serialVersionUID = 1L;

  /** タスク ID. */
  private final Long id;

  /** タイトル. */
  private final String title;

  /** 状態. */
  private final TaskStatus status;

  /** 作成日時. */
  private final Instant createdAt;

  /** 完了日時（未完了時は null）. */
  private final Instant completedAt;
}
