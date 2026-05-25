package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Objects;
import jp.mufg.it.rcb.application.port.in.FindLeaveUseCase;
import jp.mufg.it.rcb.application.port.in.LeaveRequestDetail;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.service.ApprovalPolicy;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.shared.config.AppConfig;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import jp.mufg.it.rcb.userinfo.dto.UserPositionDto;

/** 休暇申請詳細取得ユースケースの実装（読み取り専用、canApprove / canReject フラグ計算込み）. */
@ApplicationScoped
public class FindLeaveService implements FindLeaveUseCase {

  /** 永続化 port. */
  private final LeaveRepositoryPort repository;

  /** ログイン中ユーザ情報コンテキスト. */
  private final UserInfoContext userInfoContext;

  /** 承認可否判定のドメインサービス. */
  private final ApprovalPolicy approvalPolicy;

  /** アプリケーション設定（部長層コード集合の参照元）. */
  private final AppConfig appConfig;

  /**
   * 依存を注入する.
   *
   * @param repository 永続化 port
   * @param userInfoContext ログイン中ユーザ情報コンテキスト
   * @param approvalPolicy 承認可否判定のドメインサービス
   * @param appConfig アプリケーション設定
   */
  @Inject
  public FindLeaveService(
      final LeaveRepositoryPort repository,
      final UserInfoContext userInfoContext,
      final ApprovalPolicy approvalPolicy,
      final AppConfig appConfig) {
    this.repository = repository;
    this.userInfoContext = userInfoContext;
    this.approvalPolicy = approvalPolicy;
    this.appConfig = appConfig;
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public LeaveRequestDetail find(final long leaveRequestId) {
    final LeaveRequest req =
        repository
            .findById(leaveRequestId)
            .orElseThrow(
                () -> new MSTBusinessException("指定された休暇申請が存在しません（id=" + leaveRequestId + "）"));

    final UserDto user = userInfoContext.getUser();
    // 認証時に必ず設定される前提のため、主務所属が欠落していれば早期検知する。
    final UserPositionDto position =
        Objects.requireNonNull(user.getMainUserPositionDto(), "main user position is required");
    final boolean isSelf = req.getApplicantEmpNum().equals(user.getEmpNum());
    final boolean isPending = req.getStatus() == LeaveStatus.PENDING;
    final boolean authorized =
        approvalPolicy.canApprove(
            req.getApplicantOrgId(),
            position.getOrgId(),
            position.getLayerSCode(),
            appConfig.getManagerLayerCodes());
    final boolean canApprove = !isSelf && isPending && authorized;
    final boolean canReject = canApprove;

    return LeaveRequestDetail.builder()
        .id(req.getId())
        .applicantEmpNum(req.getApplicantEmpNum())
        .applicantName(req.getApplicantName())
        .applicantOrgId(req.getApplicantOrgId())
        .applicantOrgName(req.getApplicantOrgName())
        .leaveType(req.getLeaveType())
        .period(req.getPeriod())
        .reason(req.getReason())
        .status(req.getStatus())
        .appliedAt(req.getAppliedAt())
        .judgeEmpNum(req.getJudgeEmpNum())
        .judgeName(req.getJudgeName())
        .judgedAt(req.getJudgedAt())
        .judgeComment(req.getJudgeComment())
        .canApprove(canApprove)
        .canReject(canReject)
        .build();
  }
}
