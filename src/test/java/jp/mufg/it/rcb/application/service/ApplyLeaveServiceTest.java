package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.ApplyLeaveCommand;
import jp.mufg.it.rcb.application.port.in.ApplyLeaveResult;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.application.service.support.UserInfoContextFactory;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ApplyLeaveService の単体テスト. */
@ExtendWith(MockitoExtension.class)
class ApplyLeaveServiceTest {

  /** 永続化 port のモック. */
  @Mock private LeaveRepositoryPort repository;

  /** Logger のモック（@InjectLogger による CDI 注入がテスト環境では行われないため）. */
  @Mock private Logger sysLogger;

  /** 固定時刻クロック（申請日時の検証用）. */
  private final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-05-20T09:00:00Z"));

  /** 申請者のユーザコンテキスト. */
  private final UserInfoContext userInfoContext =
      UserInfoContextFactory.create("E0001", "山田 太郎", "ORG-001", "リテール開発部 第一課", "L1");

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ ApplyLeaveServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * Service を構築し、CDI で注入される sysLogger に mock を差し込む.
   *
   * <p>同パッケージ可視性 (package-private) を利用してリフレクションを使わず直接代入する。
   */
  private ApplyLeaveService newService() {
    final ApplyLeaveService svc = new ApplyLeaveService(repository, clock, userInfoContext);
    svc.sysLogger = sysLogger;
    return svc;
  }

  /**
   * 申請受付で PENDING 状態の申請が snapshot と申請日時付きで保存される.
   *
   * <p>1 受付に対する snapshot 全項目検証は単一観点として UnitTestContainsTooManyAsserts を抑制。
   */
  @Test
  void persistsPendingRequestWithApplicantSnapshot() {
    when(repository.save(any()))
        .thenAnswer(
            inv -> {
              final LeaveRequest req = inv.getArgument(0);
              return LeaveRequest.reconstruct(
                  42L,
                  req.getApplicantEmpNum(),
                  req.getApplicantName(),
                  req.getApplicantOrgId(),
                  req.getApplicantOrgName(),
                  req.getLeaveType(),
                  req.getPeriod(),
                  req.getReason(),
                  req.getStatus(),
                  req.getAppliedAt(),
                  req.getJudgeEmpNum(),
                  req.getJudgeName(),
                  req.getJudgedAt(),
                  req.getJudgeComment());
            });

    final ApplyLeaveService svc = newService();
    final ApplyLeaveCommand cmd =
        ApplyLeaveCommand.builder()
            .leaveType(LeaveType.PAID)
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(LocalDate.of(2026, 6, 3))
            .reason("私用のため")
            .build();

    final ApplyLeaveResult result = svc.apply(cmd);

    assertThat(result.getLeaveRequestId()).isEqualTo(42L);

    final ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
    verify(repository).save(captor.capture());
    final LeaveRequest saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
    assertThat(saved.getApplicantEmpNum()).isEqualTo("E0001");
    assertThat(saved.getApplicantName()).isEqualTo("山田 太郎");
    assertThat(saved.getApplicantOrgId()).isEqualTo("ORG-001");
    assertThat(saved.getApplicantOrgName()).isEqualTo("リテール開発部 第一課");
    assertThat(saved.getReason()).isEqualTo("私用のため");
    assertThat(saved.getAppliedAt()).isEqualTo(Instant.parse("2026-05-20T09:00:00Z"));
  }
}
