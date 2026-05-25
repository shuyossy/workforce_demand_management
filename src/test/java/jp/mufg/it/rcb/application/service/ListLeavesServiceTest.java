package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jp.mufg.it.rcb.application.port.in.LeaveRequestSummary;
import jp.mufg.it.rcb.application.port.in.ListLeavesCommand;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.application.service.support.UserInfoContextFactory;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ListLeavesService の単体テスト. */
@ExtendWith(MockitoExtension.class)
class ListLeavesServiceTest {

  /** ログインユーザの社員番号（一覧 scope 判定の基準値）. */
  private static final String LOGIN_EMP_NUM = "E0001";

  /** 永続化 port のモック. */
  @Mock private LeaveRepositoryPort repository;

  /** ログインユーザのコンテキスト（E0001 / ORG-001 / 一般層 L1）. */
  private final UserInfoContext userInfoContext =
      UserInfoContextFactory.create(LOGIN_EMP_NUM, "山田 太郎", "ORG-001", "リテール開発部 第一課", "L1");

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ ListLeavesServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * 指定 ID / 申請者の休暇申請を新規生成する.
   *
   * @param id 休暇申請 ID
   * @param empNum 申請者社員番号
   * @param status 申請状態
   * @return 構築済み LeaveRequest
   */
  private LeaveRequest sample(final long id, final String empNum, final LeaveStatus status) {
    return LeaveRequest.reconstruct(
        id,
        empNum,
        "name",
        "ORG-001",
        "リテール開発部 第一課",
        LeaveType.PAID,
        new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
        "理由",
        status,
        Instant.parse("2026-05-20T09:00:00Z"),
        null,
        null,
        null,
        null);
  }

  /** scope=MINE：自分が申請した一覧を返す（件数と applicantEmpNum が自分のものか）. */
  @Test
  void mineScopeReturnsCurrentUserRequests() {
    when(repository.findByApplicantEmpNum(LOGIN_EMP_NUM))
        .thenReturn(
            List.of(
                sample(1L, LOGIN_EMP_NUM, LeaveStatus.APPROVED),
                sample(2L, LOGIN_EMP_NUM, LeaveStatus.PENDING)));

    final ListLeavesService svc = new ListLeavesService(repository, userInfoContext);
    final List<LeaveRequestSummary> result =
        svc.list(ListLeavesCommand.builder().scope(ListLeavesCommand.Scope.MINE).build());

    assertThat(result).hasSize(2).allMatch(s -> LOGIN_EMP_NUM.equals(s.getApplicantEmpNum()));
  }

  /** scope=APPROVABLE：同 orgId かつ PENDING かつ 自分以外の申請を返す. */
  @Test
  void approvableScopeReturnsPendingByOrgExcludingSelf() {
    when(repository.findApprovablePending("ORG-001", LOGIN_EMP_NUM))
        .thenReturn(List.of(sample(3L, "E0004", LeaveStatus.PENDING)));

    final ListLeavesService svc = new ListLeavesService(repository, userInfoContext);
    final List<LeaveRequestSummary> result =
        svc.list(ListLeavesCommand.builder().scope(ListLeavesCommand.Scope.APPROVABLE).build());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getApplicantEmpNum()).isEqualTo("E0004");
  }
}
