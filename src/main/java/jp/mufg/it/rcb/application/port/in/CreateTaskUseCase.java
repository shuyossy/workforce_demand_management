package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.Valid;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加も想定するため lambda 化を意図しない。
/** タスク新規作成ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface CreateTaskUseCase {

  /**
   * タスクを新規作成して永続化する.
   *
   * @param command 入力コマンド
   */
  void create(@Valid CreateTaskCommand command);
}
