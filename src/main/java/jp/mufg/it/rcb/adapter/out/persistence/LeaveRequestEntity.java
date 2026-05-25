package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 休暇申請テーブル（leave_request）の JPA Entity. */
@Entity
@Table(name = "leave_request")
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequestEntity {

  /** 主キー（DB IDENTITY 採番）. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 申請者の社員番号. */
  @Column(name = "applicant_emp_num", nullable = false, length = 20)
  private String applicantEmpNum;

  /** 申請者の氏名. */
  @Column(name = "applicant_name", nullable = false, length = 100)
  private String applicantName;

  /** 申請者の所属組織 ID. */
  @Column(name = "applicant_org_id", nullable = false, length = 20)
  private String applicantOrgId;

  /** 申請者の所属組織名. */
  @Column(name = "applicant_org_name", nullable = false, length = 200)
  private String applicantOrgName;

  /** 休暇種別. */
  @Enumerated(EnumType.STRING)
  @Column(name = "leave_type", nullable = false, length = 20)
  private LeaveType leaveType;

  /** 休暇開始日. */
  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  /** 休暇終了日. */
  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  /** 申請理由. */
  @Column(name = "reason", nullable = false, length = 500)
  private String reason;

  /** 申請ステータス. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LeaveStatus status;

  /** 申請日時. */
  @Column(name = "applied_at", nullable = false)
  private Instant appliedAt;

  /** 判断者の社員番号. */
  @Column(name = "judge_emp_num", length = 20)
  private String judgeEmpNum;

  /** 判断者の氏名. */
  @Column(name = "judge_name", length = 100)
  private String judgeName;

  /** 判断日時. */
  @Column(name = "judged_at")
  private Instant judgedAt;

  /** 判断者コメント. */
  @Column(name = "judge_comment", length = 500)
  private String judgeComment;
}
