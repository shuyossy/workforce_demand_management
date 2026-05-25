package jp.mufg.it.rcb.application.port.in;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加（バリエーション化）も想定するため lambda 化を意図しない。
/** 休暇申請詳細取得ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface FindLeaveUseCase {

  /**
   * 指定された休暇申請の詳細を取得する.
   *
   * @param leaveRequestId 取得対象の休暇申請 ID
   * @return 詳細表示用 DTO
   */
  LeaveRequestDetail find(long leaveRequestId);
}
