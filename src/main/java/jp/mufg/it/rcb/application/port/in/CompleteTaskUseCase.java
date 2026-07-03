package jp.mufg.it.rcb.application.port.in;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加も想定するため lambda 化を意図しない。
/** タスク完了ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface CompleteTaskUseCase {

  /**
   * タスクを完了する.
   *
   * @param taskId 対象タスク ID
   */
  void complete(long taskId);
}
