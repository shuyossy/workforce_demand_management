package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;

/** ドメイン LeaveRequest と JPA Entity LeaveRequestEntity を相互変換するマッパー. */
@ApplicationScoped
public class LeaveRequestMapper {

  /** デフォルトコンストラクタ（CDI 仕様により公開する）. */
  public LeaveRequestMapper() {
    // CDI が ApplicationScoped Bean として生成するため初期化処理は不要。
  }

  /**
   * ドメインから新規 Entity を生成する.
   *
   * @param domain 変換元ドメイン（null 不可）
   * @return 新規生成された {@link LeaveRequestEntity}
   */
  public LeaveRequestEntity toEntity(final LeaveRequest domain) {
    final LeaveRequestEntity entity = new LeaveRequestEntity();
    applyToEntity(domain, entity);
    return entity;
  }

  /**
   * 既存 Entity にドメインの値を反映する（in-place 更新）.
   *
   * @param src 反映元ドメイン（null 不可）
   * @param dst 反映先 Entity（null 不可）
   */
  public void applyToEntity(final LeaveRequest src, final LeaveRequestEntity dst) {
    dst.setId(src.getId());
    dst.setApplicantEmpNum(src.getApplicantEmpNum());
    dst.setApplicantName(src.getApplicantName());
    dst.setApplicantOrgId(src.getApplicantOrgId());
    dst.setApplicantOrgName(src.getApplicantOrgName());
    dst.setLeaveType(src.getLeaveType());
    dst.setStartDate(src.getPeriod().getStartDate());
    dst.setEndDate(src.getPeriod().getEndDate());
    dst.setReason(src.getReason());
    dst.setStatus(src.getStatus());
    dst.setAppliedAt(src.getAppliedAt());
    dst.setJudgeEmpNum(src.getJudgeEmpNum());
    dst.setJudgeName(src.getJudgeName());
    dst.setJudgedAt(src.getJudgedAt());
    dst.setJudgeComment(src.getJudgeComment());
  }

  /**
   * Entity をドメインへ復元する.
   *
   * @param entity 変換元 Entity（null 不可）
   * @return 復元された {@link LeaveRequest}
   */
  public LeaveRequest toDomain(final LeaveRequestEntity entity) {
    return LeaveRequest.reconstruct(
        entity.getId(),
        entity.getApplicantEmpNum(),
        entity.getApplicantName(),
        entity.getApplicantOrgId(),
        entity.getApplicantOrgName(),
        entity.getLeaveType(),
        new LeavePeriod(entity.getStartDate(), entity.getEndDate()),
        entity.getReason(),
        entity.getStatus(),
        entity.getAppliedAt(),
        entity.getJudgeEmpNum(),
        entity.getJudgeName(),
        entity.getJudgedAt(),
        entity.getJudgeComment());
  }
}
