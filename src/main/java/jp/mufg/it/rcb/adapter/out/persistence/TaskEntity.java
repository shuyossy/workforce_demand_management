package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** タスクテーブル（task）の JPA Entity. */
@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
public class TaskEntity {

  /** 主キー（DB IDENTITY 採番）. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** タイトル. */
  @Column(name = "title", nullable = false, length = 100)
  private String title;

  /** 状態. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 4)
  private TaskStatus status;

  /** 作成日時. */
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** 完了日時（未完了時 null）. */
  @Column(name = "completed_at")
  private Instant completedAt;
}
