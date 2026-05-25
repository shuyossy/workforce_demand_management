package jp.mufg.it.rcb.application.port.in;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 休暇申請ユースケースの実行結果（採番済み leaveRequestId を返す）. */
@Getter
@AllArgsConstructor
public class ApplyLeaveResult {

  /** 採番済みの休暇申請 ID. */
  private final long leaveRequestId;
}
