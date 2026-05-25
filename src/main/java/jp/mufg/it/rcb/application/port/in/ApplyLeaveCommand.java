package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import jp.mufg.it.rcb.domain.model.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 休暇申請ユースケースの入力コマンド.
 *
 * <p>Bean Validation で必須項目と期間整合性（BR-INV-001）を検証する。
 */
@Getter
@Builder
@AllArgsConstructor
public class ApplyLeaveCommand {

  /** 休暇種別（null 不可）. */
  @NotNull private final LeaveType leaveType;

  /** 期間の開始日（null 不可）. */
  @NotNull private final LocalDate startDate;

  /** 期間の終了日（null 不可、startDate 以上）. */
  @NotNull private final LocalDate endDate;

  /** 申請理由（必須、500 字以内）. */
  @NotBlank
  @Size(max = 500)
  private final String reason;

  /**
   * 期間整合性の Bean Validation 用判定（startDate &lt;= endDate）.
   *
   * <p>いずれかが null の場合は {@code @NotNull} 側で検出するため true を返す。
   *
   * @return 開始日が終了日以前であれば true
   */
  @AssertTrue(message = "開始日は終了日以前である必要があります")
  public boolean isValidPeriod() {
    return startDate == null || endDate == null || !startDate.isAfter(endDate);
  }
}
