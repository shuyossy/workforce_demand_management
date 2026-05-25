package jp.mufg.it.rcb.domain.model;

import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

// PMD 抑制理由:
//  - ExcessiveParameterList / UseObjectForClearerAPI: 14 フィールドのドメインエンティティに
//    対する factory / コンストラクタはフィールドと 1:1 で対応するため Builder 化はオーバースペック。
/** 休暇申請エンティティ（PENDING → APPROVED / REJECTED の状態遷移と判断者 snapshot を担う）. */
@Getter
@SuppressWarnings({"PMD.ExcessiveParameterList", "PMD.UseObjectForClearerAPI"})
public final class LeaveRequest {

  /** 永続化 ID（新規作成時は null）. */
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

  /** 現在の状態. */
  private LeaveStatus status;

  /** 申請日時. */
  private final Instant appliedAt;

  /** 判断者の社員番号（判断前は null）. */
  private String judgeEmpNum;

  /** 判断者の氏名（判断前は null）. */
  private String judgeName;

  /** 判断日時（判断前は null）. */
  private Instant judgedAt;

  /** 判断時のコメント（approve は任意、reject は必須）. */
  private String judgeComment;

  /** フィールドを一括設定する内部コンストラクタ（id / judge 系 4 項目以外は null 不可）. */
  private LeaveRequest(
      final Long id,
      final String applicantEmpNum,
      final String applicantName,
      final String applicantOrgId,
      final String applicantOrgName,
      final LeaveType leaveType,
      final LeavePeriod period,
      final String reason,
      final LeaveStatus status,
      final Instant appliedAt,
      final String judgeEmpNum,
      final String judgeName,
      final Instant judgedAt,
      final String judgeComment) {
    this.id = id;
    this.applicantEmpNum = Objects.requireNonNull(applicantEmpNum, "applicantEmpNum");
    this.applicantName = Objects.requireNonNull(applicantName, "applicantName");
    this.applicantOrgId = Objects.requireNonNull(applicantOrgId, "applicantOrgId");
    this.applicantOrgName = Objects.requireNonNull(applicantOrgName, "applicantOrgName");
    this.leaveType = Objects.requireNonNull(leaveType, "leaveType");
    this.period = Objects.requireNonNull(period, "period");
    this.reason = Objects.requireNonNull(reason, "reason");
    this.status = Objects.requireNonNull(status, "status");
    this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt");
    this.judgeEmpNum = judgeEmpNum;
    this.judgeName = judgeName;
    this.judgedAt = judgedAt;
    this.judgeComment = judgeComment;
  }

  /**
   * 新規申請を生成する（status は PENDING 固定、全引数 null 不可）.
   *
   * @return PENDING 状態の新規 LeaveRequest
   */
  public static LeaveRequest create(
      final String applicantEmpNum,
      final String applicantName,
      final String applicantOrgId,
      final String applicantOrgName,
      final LeaveType leaveType,
      final LeavePeriod period,
      final String reason,
      final Instant appliedAt) {
    return new LeaveRequest(
        null,
        applicantEmpNum,
        applicantName,
        applicantOrgId,
        applicantOrgName,
        leaveType,
        period,
        reason,
        LeaveStatus.PENDING,
        appliedAt,
        null,
        null,
        null,
        null);
  }

  /**
   * 永続化層から復元するためのファクトリ（テスト・Mapper 用、judge 系 4 項目以外は null 不可）.
   *
   * @return 復元された LeaveRequest
   */
  public static LeaveRequest reconstruct(
      final Long id,
      final String applicantEmpNum,
      final String applicantName,
      final String applicantOrgId,
      final String applicantOrgName,
      final LeaveType leaveType,
      final LeavePeriod period,
      final String reason,
      final LeaveStatus status,
      final Instant appliedAt,
      final String judgeEmpNum,
      final String judgeName,
      final Instant judgedAt,
      final String judgeComment) {
    return new LeaveRequest(
        id,
        applicantEmpNum,
        applicantName,
        applicantOrgId,
        applicantOrgName,
        leaveType,
        period,
        reason,
        status,
        appliedAt,
        judgeEmpNum,
        judgeName,
        judgedAt,
        judgeComment);
  }

  /**
   * 承認する.
   *
   * <p>PENDING からのみ遷移可能（BR-INV-002 / BR-PRE-002）。判断者情報を snapshot 保存する。
   *
   * @param judgeEmpNum 判断者社員番号（null 不可）
   * @param judgeName 判断者氏名（null 不可）
   * @param judgedAt 判断日時（null 不可）
   * @param comment 判断コメント（任意）
   * @throws IllegalStateException 現在の状態が PENDING でない場合
   */
  public void approve(
      final String judgeEmpNum,
      final String judgeName,
      final Instant judgedAt,
      final String comment) {
    requirePending();
    this.status = LeaveStatus.APPROVED;
    this.judgeEmpNum = Objects.requireNonNull(judgeEmpNum, "judgeEmpNum");
    this.judgeName = Objects.requireNonNull(judgeName, "judgeName");
    this.judgedAt = Objects.requireNonNull(judgedAt, "judgedAt");
    this.judgeComment = comment;
  }

  /**
   * 却下する.
   *
   * <p>PENDING からのみ遷移可能（BR-INV-002 / BR-PRE-002）。コメント必須。
   *
   * @param judgeEmpNum 判断者社員番号（null 不可）
   * @param judgeName 判断者氏名（null 不可）
   * @param judgedAt 判断日時（null 不可）
   * @param comment 却下理由コメント（null 不可）
   * @throws IllegalStateException 現在の状態が PENDING でない場合
   */
  public void reject(
      final String judgeEmpNum,
      final String judgeName,
      final Instant judgedAt,
      final String comment) {
    requirePending();
    Objects.requireNonNull(comment, "comment is required on reject");
    this.status = LeaveStatus.REJECTED;
    this.judgeEmpNum = Objects.requireNonNull(judgeEmpNum, "judgeEmpNum");
    this.judgeName = Objects.requireNonNull(judgeName, "judgeName");
    this.judgedAt = Objects.requireNonNull(judgedAt, "judgedAt");
    this.judgeComment = comment;
  }

  /** 現在の状態が PENDING でなければ IllegalStateException を投げる. */
  private void requirePending() {
    if (this.status != LeaveStatus.PENDING) {
      throw new IllegalStateException(
          "Only PENDING requests can transition; current=" + this.status);
    }
  }
}
