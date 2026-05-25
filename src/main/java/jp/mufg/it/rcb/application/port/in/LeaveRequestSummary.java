package jp.mufg.it.rcb.application.port.in;

import java.io.Serializable;
import java.time.Instant;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 休暇申請一覧表示用 DTO（snapshot 値を保持）.
 *
 * <p>@ViewScoped バッキングビーン（LeaveListBean）のフィールドとして保持されるため Serializable。
 */
@Getter
@Builder
@AllArgsConstructor
public class LeaveRequestSummary implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 休暇申請 ID. */
  private final Long id;

  /** 申請者の社員番号. */
  private final String applicantEmpNum;

  /** 申請者の氏名. */
  private final String applicantName;

  /** 休暇種別. */
  private final LeaveType leaveType;

  /** 休暇期間. */
  private final LeavePeriod period;

  /** 申請状態. */
  private final LeaveStatus status;

  /** 申請日時. */
  private final Instant appliedAt;
}
