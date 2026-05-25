package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 休暇承認ユースケースの入力コマンド. */
@Getter
@Builder
@AllArgsConstructor
public class ApproveLeaveCommand {

  /** 対象となる休暇申請 ID（null 不可）. */
  @NotNull private final Long leaveRequestId;

  /** 承認コメント（任意、500 字以内）. */
  @Size(max = 500)
  private final String comment;
}
