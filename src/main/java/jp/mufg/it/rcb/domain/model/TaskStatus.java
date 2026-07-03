package jp.mufg.it.rcb.domain.model;

/** タスクの状態. */
public enum TaskStatus {
  /** 未完了. */
  TODO,
  /** 完了. */
  DONE;

  /**
   * 完了操作が可能か判定する. 完了は未完了（TODO）からのみ可能。
   *
   * @return 完了可能なら true
   */
  public boolean canComplete() {
    return this == TODO;
  }
}
