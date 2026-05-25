package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 休暇却下ユースケースの入力コマンド.
 *
 * <p>コメントは BR-PRE-004 により必須。
 */
@Getter
@Builder
@AllArgsConstructor
public class RejectLeaveCommand {

  /** 対象となる休暇申請 ID（null 不可）. */
  @NotNull private final Long leaveRequestId;

  /** 却下コメント（必須、500 字以内）. */
  @NotBlank
  @Size(max = 500)
  private final String comment;
}
