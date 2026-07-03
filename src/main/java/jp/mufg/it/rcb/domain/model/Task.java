package jp.mufg.it.rcb.domain.model;

import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/** タスクエンティティ（TODO → DONE の状態遷移を担う集約ルート）. */
// PMD 抑制理由:
//  - ShortClassName: "Task" はドメインのユビキタス言語における集約ルート名（domain-glossary.md 準拠）であり、
//    4 文字だが業務用語として正当。ドメイン内で唯一の短命名かつ単発の例外のため、rules/README.md §4.5 の
//    フローに従い ruleset 改定ではなく個別抑制する。
@SuppressWarnings("PMD.ShortClassName")
@Getter
public final class Task {

  /** 永続化 ID（新規作成時は null）. */
  private final Long id;

  /** タイトル（必須・非空白）. */
  private final String title;

  /** 現在の状態. */
  private TaskStatus status;

  /** 作成日時. */
  private final Instant createdAt;

  /** 完了日時（未完了時は null）. */
  private Instant completedAt;

  /** フィールドを一括設定する内部コンストラクタ（id / completedAt 以外は null 不可）. */
  private Task(
      final Long id,
      final String title,
      final TaskStatus status,
      final Instant createdAt,
      final Instant completedAt) {
    this.id = id;
    this.title = requireNonBlank(title);
    this.status = Objects.requireNonNull(status, "status");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.completedAt = completedAt;
  }

  /**
   * 新規タスクを生成する（status は TODO 固定、completedAt は null）.
   *
   * @param title タイトル（必須・非空白）
   * @param createdAt 作成日時（null 不可）
   * @return TODO 状態の新規 Task
   */
  public static Task create(final String title, final Instant createdAt) {
    return new Task(null, title, TaskStatus.TODO, createdAt, null);
  }

  /**
   * 永続化層から復元するためのファクトリ（Mapper・テスト用）.
   *
   * @param id 永続化 ID
   * @param title タイトル
   * @param status 状態
   * @param createdAt 作成日時
   * @param completedAt 完了日時（未完了時 null）
   * @return 復元された Task
   */
  public static Task reconstruct(
      final Long id,
      final String title,
      final TaskStatus status,
      final Instant createdAt,
      final Instant completedAt) {
    return new Task(id, title, status, createdAt, completedAt);
  }

  /**
   * タスクを完了する.
   *
   * <p>TODO からのみ遷移可能。既に DONE の場合はドメイン不変条件違反として {@link IllegalStateException} を投げる （UseCase
   * 側で事前に状態を再評価し業務エラーへ翻訳する前提の、到達したら設計バグとなる防御）。
   *
   * @param completedAt 完了日時（null 不可）
   * @throws IllegalStateException 現在の状態が TODO でない場合
   */
  public void complete(final Instant completedAt) {
    if (!this.status.canComplete()) {
      throw new IllegalStateException("Only TODO tasks can be completed; current=" + this.status);
    }
    this.status = TaskStatus.DONE;
    this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
  }

  /** タイトルの非空白を検証する. */
  private static String requireNonBlank(final String title) {
    Objects.requireNonNull(title, "title");
    if (title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    return title;
  }
}
