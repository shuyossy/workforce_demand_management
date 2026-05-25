package jp.mufg.it.rcb.domain.model;

import java.io.Serializable;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;

// 不変条件: startDate <= endDate（BR-INV-001）。
// @ViewScoped バッキングビーンから DTO 経由で参照されるため Serializable。
/** 休暇期間を表す値オブジェクト. */
@Getter
@EqualsAndHashCode
public final class LeavePeriod implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 期間の開始日（包含、null 不可）. */
  private final LocalDate startDate;

  /** 期間の終了日（包含、null 不可、startDate 以上）. */
  private final LocalDate endDate;

  /**
   * 期間を生成する.
   *
   * @param startDate 開始日（null 不可）
   * @param endDate 終了日（null 不可、startDate 以上）
   * @throws IllegalArgumentException 引数が null、または startDate が endDate より後の場合
   */
  public LeavePeriod(final LocalDate startDate, final LocalDate endDate) {
    if (startDate == null) {
      throw new IllegalArgumentException("startDate must not be null");
    }
    if (endDate == null) {
      throw new IllegalArgumentException("endDate must not be null");
    }
    if (startDate.isAfter(endDate)) {
      throw new IllegalArgumentException(
          "start (" + startDate + ") must be on or before end (" + endDate + ")");
    }
    this.startDate = startDate;
    this.endDate = endDate;
  }
}
