package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.Valid;
import java.util.List;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: ユースケース port は CDI Service Bean による実装を前提とし、
//    将来のメソッド追加（バリエーション化）も想定するため lambda 化を意図しない。
/** 休暇申請一覧ユースケースの入力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ListLeavesUseCase {

  /**
   * 指定された取得範囲の休暇申請一覧を返す.
   *
   * @param command 入力コマンド
   * @return 取得範囲に該当する休暇申請の表示用 DTO 一覧
   */
  List<LeaveRequestSummary> list(@Valid ListLeavesCommand command);
}
