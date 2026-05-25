package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.ApproveLeaveCommand;
import jp.mufg.it.rcb.application.port.in.ApproveLeaveUseCase;
import jp.mufg.it.rcb.application.port.out.ClockPort;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.service.ApprovalPolicy;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;
import jp.mufg.it.rcb.shared.config.AppConfig;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import jp.mufg.it.rcb.userinfo.dto.UserPositionDto;

/** 休暇承認ユースケースの実装（BR-PRE-001/002/005, BR-POL-001）. */
@ApplicationScoped
public class ApproveLeaveService implements ApproveLeaveUseCase {

  /** 永続化 port. */
  private final LeaveRepositoryPort repository;

  /** 時刻取得 port. */
  private final ClockPort clock;

  /** ログイン中ユーザ情報コンテキスト. */
  private final UserInfoContext userInfoContext;

  /** 承認可否判定のドメインサービス. */
  private final ApprovalPolicy approvalPolicy;

  /** アプリケーション設定（部長層コード集合の参照元）. */
  private final AppConfig appConfig;

  /**
   * 業務イベント用 Logger（messages.properties をカスタム Formatter 経由で解決）.
   *
   * <p>パッケージ private 可視性は同パッケージのテストから直接 mock を差し込めるよう意図的に採用している。
   */
  @Inject
  @InjectLogger(LoggerType.SYSTEM)
  /* default */ Logger sysLogger;

  /**
   * 依存を注入する.
   *
   * @param repository 永続化 port
   * @param clock 時刻取得 port
   * @param userInfoContext ログイン中ユーザ情報コンテキスト
   * @param approvalPolicy 承認可否判定のドメインサービス
   * @param appConfig アプリケーション設定
   */
  @Inject
  public ApproveLeaveService(
      final LeaveRepositoryPort repository,
      final ClockPort clock,
      final UserInfoContext userInfoContext,
      final ApprovalPolicy approvalPolicy,
      final AppConfig appConfig) {
    this.repository = repository;
    this.clock = clock;
    this.userInfoContext = userInfoContext;
    this.approvalPolicy = approvalPolicy;
    this.appConfig = appConfig;
  }

  @Override
  @Transactional
  public void approve(@Valid final ApproveLeaveCommand cmd) {
    final LeaveRequest req =
        repository
            .findById(cmd.getLeaveRequestId())
            .orElseThrow(
                () ->
                    new MSTBusinessException(
                        "指定された休暇申請が存在しません（id=" + cmd.getLeaveRequestId() + "）"));

    if (req.getStatus() != LeaveStatus.PENDING) {
      throw new MSTBusinessException("申請中の休暇申請のみ承認/却下できます（現在の状態=" + req.getStatus().name() + "）");
    }

    final UserDto user = userInfoContext.getUser();
    if (req.getApplicantEmpNum().equals(user.getEmpNum())) {
      throw new MSTBusinessException("自分の申請は承認/却下できません");
    }

    // 認証時に必ず設定される前提のため、主務所属が欠落していれば早期検知する。
    final UserPositionDto position =
        Objects.requireNonNull(user.getMainUserPositionDto(), "main user position is required");
    if (!isAuthorizedJudge(req, position)) {
      throw new MSTBusinessException("この休暇申請を承認/却下する権限がありません");
    }

    req.approve(user.getEmpNum(), user.getName(), clock.now(), cmd.getComment());
    repository.save(req);
    // PMD GuardLogStatement: Object[] パラメータ配列生成を回避するため Level ガードを挟む.
    if (sysLogger.isLoggable(Level.INFO)) {
      sysLogger.log(Level.INFO, "RCB00002-I", new Object[] {req.getId(), req.getApplicantName()});
    }
  }

  /**
   * 判断者の主務所属が承認可能か {@link ApprovalPolicy} で評価する.
   *
   * @param req 判断対象の休暇申請
   * @param position 判断者の主務所属（非 null）
   * @return 承認可能なら true
   */
  private boolean isAuthorizedJudge(final LeaveRequest req, final UserPositionDto position) {
    return approvalPolicy.canApprove(
        req.getApplicantOrgId(),
        position.getOrgId(),
        position.getLayerSCode(),
        appConfig.getManagerLayerCodes());
  }
}
