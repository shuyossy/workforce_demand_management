package jp.mufg.it.rcb.application.port.in;

import java.util.List;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加も想定するため lambda 化を意図しない。
/** タスク一覧取得ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ListTasksUseCase {

  /**
   * 全タスクを作成日時の降順で取得する.
   *
   * @return 一覧表示用 DTO の一覧
   */
  List<TaskSummary> list();
}
