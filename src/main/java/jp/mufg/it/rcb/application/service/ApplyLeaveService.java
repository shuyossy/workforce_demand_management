package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.ApplyLeaveCommand;
import jp.mufg.it.rcb.application.port.in.ApplyLeaveResult;
import jp.mufg.it.rcb.application.port.in.ApplyLeaveUseCase;
import jp.mufg.it.rcb.application.port.out.ClockPort;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import jp.mufg.it.rcb.userinfo.dto.UserPositionDto;

/** 休暇申請ユースケースの実装（成功時に業務イベント INFO ログを emit）. */
@ApplicationScoped
public class ApplyLeaveService implements ApplyLeaveUseCase {

  /** 永続化 port. */
  private final LeaveRepositoryPort repository;

  /** 時刻取得 port. */
  private final ClockPort clock;

  /** ログイン中ユーザ情報コンテキスト. */
  private final UserInfoContext userInfoContext;

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
   */
  @Inject
  public ApplyLeaveService(
      final LeaveRepositoryPort repository,
      final ClockPort clock,
      final UserInfoContext userInfoContext) {
    this.repository = repository;
    this.clock = clock;
    this.userInfoContext = userInfoContext;
  }

  @Override
  @Transactional
  public ApplyLeaveResult apply(@Valid final ApplyLeaveCommand cmd) {
    final UserDto user = userInfoContext.getUser();
    // 申請時は主務所属（orgId / orgName）が必須。認証段階で必ず設定される前提のため、
    // 欠落していれば IllegalStateException で早期検知する。
    final UserPositionDto position =
        Objects.requireNonNull(
            user.getMainUserPositionDto(), "main user position is required for leave application");
    final LeaveRequest request =
        LeaveRequest.create(
            user.getEmpNum(),
            user.getName(),
            position.getOrgId(),
            position.getGroupName(),
            cmd.getLeaveType(),
            new LeavePeriod(cmd.getStartDate(), cmd.getEndDate()),
            cmd.getReason(),
            clock.now());
    final LeaveRequest saved = repository.save(request);
    // PMD GuardLogStatement: Object[] パラメータ配列生成を回避するため Level ガードを挟む.
    if (sysLogger.isLoggable(Level.INFO)) {
      sysLogger.log(Level.INFO, "RCB00001-I", new Object[] {saved.getId()});
    }
    return new ApplyLeaveResult(saved.getId());
  }
}
