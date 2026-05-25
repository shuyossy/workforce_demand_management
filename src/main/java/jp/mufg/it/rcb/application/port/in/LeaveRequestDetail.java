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
 * 休暇申請詳細表示用 DTO（snapshot 値 + canApprove / canReject フラグ）.
 *
 * <p>@ViewScoped バッキングビーン（LeaveDetailBean）のフィールドとして保持されるため Serializable。
 */
@Getter
@Builder
@AllArgsConstructor
public class LeaveRequestDetail implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 休暇申請 ID. */
  private final Long id;

  /** 申請者の社員番号. */
  private final String applicantEmpNum;

  /** 申請者の氏名. */
  private final String applicantName;

  /** 申請者の所属組織 ID. */
  private final String applicantOrgId;

  /** 申請者の所属組織名. */
  private final String applicantOrgName;

  /** 休暇種別. */
  private final LeaveType leaveType;

  /** 休暇期間. */
  private final LeavePeriod period;

  /** 申請理由. */
  private final String reason;

  /** 申請状態. */
  private final LeaveStatus status;

  /** 申請日時. */
  private final Instant appliedAt;

  /** 判断者の社員番号（判断前は null）. */
  private final String judgeEmpNum;

  /** 判断者の氏名（判断前は null）. */
  private final String judgeName;

  /** 判断日時（判断前は null）. */
  private final Instant judgedAt;

  /** 判断時のコメント（判断前は null）. */
  private final String judgeComment;

  /** 現在のユーザが承認可能か. */
  private final boolean canApprove;

  /** 現在のユーザが却下可能か. */
  private final boolean canReject;
}
