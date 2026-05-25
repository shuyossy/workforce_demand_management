package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.Valid;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加（バリエーション化）も想定するため lambda 化を意図しない。
/** 休暇却下ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface RejectLeaveUseCase {

  /**
   * 指定された休暇申請を却下する.
   *
   * @param command 入力コマンド
   */
  void reject(@Valid RejectLeaveCommand command);
}
