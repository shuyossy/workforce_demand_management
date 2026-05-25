package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.Valid;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加（バリエーション化）も想定するため lambda 化を意図しない。
/** 休暇申請ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ApplyLeaveUseCase {

  /**
   * 休暇申請を受け付けて永続化する.
   *
   * @param command 入力コマンド
   * @return 採番済みの申請 ID を含む結果
   */
  ApplyLeaveResult apply(@Valid ApplyLeaveCommand command);
}
