package jp.mufg.it.rcb.application.port.out;

import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.domain.model.LeaveRequest;

/** 休暇申請の永続化を担う出力 port. */
public interface LeaveRepositoryPort {

  /**
   * 休暇申請を永続化する.
   *
   * @param request 永続化対象（新規時は ID 未採番、更新時は採番済み）
   * @return 採番済み（または更新済み）の休暇申請
   */
  LeaveRequest save(LeaveRequest request);

  /**
   * ID を指定して休暇申請を取得する.
   *
   * @param leaveRequestId 休暇申請 ID
   * @return 該当する休暇申請（存在しない場合は空）
   */
  Optional<LeaveRequest> findById(long leaveRequestId);

  /**
   * 申請者の社員番号を指定して休暇申請の一覧を取得する.
   *
   * @param empNum 申請者の社員番号
   * @return 該当する休暇申請の一覧
   */
  List<LeaveRequest> findByApplicantEmpNum(String empNum);

  /**
   * 承認待ち（PENDING）かつ申請者以外の休暇申請を、指定部署内で取得する.
   *
   * @param orgId 部署 ID
   * @param excludeEmpNum 除外する申請者の社員番号（通常はログインユーザ）
   * @return 承認可能な休暇申請の一覧
   */
  List<LeaveRequest> findApprovablePending(String orgId, String excludeEmpNum);
}
