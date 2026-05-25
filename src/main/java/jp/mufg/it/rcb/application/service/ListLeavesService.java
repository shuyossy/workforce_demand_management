package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import jp.mufg.it.rcb.application.port.in.LeaveRequestSummary;
import jp.mufg.it.rcb.application.port.in.ListLeavesCommand;
import jp.mufg.it.rcb.application.port.in.ListLeavesUseCase;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import jp.mufg.it.rcb.userinfo.dto.UserPositionDto;

/** 休暇申請一覧ユースケースの実装（scope=MINE / APPROVABLE を読み取り専用で問い合わせる）. */
@ApplicationScoped
public class ListLeavesService implements ListLeavesUseCase {

  /** 永続化 port. */
  private final LeaveRepositoryPort repository;

  /** ログイン中ユーザ情報コンテキスト. */
  private final UserInfoContext userInfoContext;

  /**
   * 依存を注入する.
   *
   * @param repository 永続化 port
   * @param userInfoContext ログイン中ユーザ情報コンテキスト
   */
  @Inject
  public ListLeavesService(
      final LeaveRepositoryPort repository, final UserInfoContext userInfoContext) {
    this.repository = repository;
    this.userInfoContext = userInfoContext;
  }

  @Override
  @Transactional(Transactional.TxType.SUPPORTS)
  public List<LeaveRequestSummary> list(@Valid final ListLeavesCommand cmd) {
    return fetchByScope(cmd.getScope()).stream().map(this::toSummary).toList();
  }

  /**
   * 取得範囲に応じた休暇申請を永続化 port から取得する.
   *
   * @param scope 取得範囲
   * @return 取得範囲に該当する休暇申請の一覧
   */
  private List<LeaveRequest> fetchByScope(final ListLeavesCommand.Scope scope) {
    final UserDto user = userInfoContext.getUser();
    return switch (scope) {
      case MINE -> repository.findByApplicantEmpNum(user.getEmpNum());
      case APPROVABLE -> {
        // 認証時に必ず設定される前提のため、主務所属が欠落していれば早期検知する。
        final UserPositionDto position =
            Objects.requireNonNull(user.getMainUserPositionDto(), "main user position is required");
        yield repository.findApprovablePending(position.getOrgId(), user.getEmpNum());
      }
    };
  }

  /**
   * LeaveRequest を表示用 DTO に変換する.
   *
   * @param req 変換対象の休暇申請
   * @return 表示用 DTO
   */
  private LeaveRequestSummary toSummary(final LeaveRequest req) {
    return LeaveRequestSummary.builder()
        .id(req.getId())
        .applicantEmpNum(req.getApplicantEmpNum())
        .applicantName(req.getApplicantName())
        .leaveType(req.getLeaveType())
        .period(req.getPeriod())
        .status(req.getStatus())
        .appliedAt(req.getAppliedAt())
        .build();
  }
}
