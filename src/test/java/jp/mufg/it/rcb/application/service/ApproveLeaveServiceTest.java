package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.ApproveLeaveCommand;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.application.service.support.UserInfoContextFactory;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import jp.mufg.it.rcb.domain.service.ApprovalPolicy;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.shared.config.AppConfig;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ApproveLeaveService の単体テスト. */
@ExtendWith(MockitoExtension.class)
class ApproveLeaveServiceTest {

  /** 永続化 port のモック. */
  @Mock private LeaveRepositoryPort repository;

  /** AppConfig のモック（MicroProfile Config 経由の解決を回避するため）. */
  @Mock private AppConfig appConfig;

  /** Logger のモック（@InjectLogger による CDI 注入がテスト環境では行われないため）. */
  @Mock private Logger sysLogger;

  /** 固定時刻クロック（判断日時の検証用）. */
  private final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-05-21T10:00:00Z"));

  /** 承認者のユーザコンテキスト（同一 ORG / 部長層 M1）. */
  private final UserInfoContext approver =
      UserInfoContextFactory.create("E0002", "佐藤 花子", "ORG-001", "リテール開発部 第一課", "M1");

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ ApproveLeaveServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * E0001 が申請した PENDING の休暇申請を新規生成する.
   *
   * <p>approve() が状態を mutate するため、テストごとに新しいインスタンスを返す必要がある。
   */
  private LeaveRequest pendingByE0001() {
    return LeaveRequest.reconstruct(
        10L,
        "E0001",
        "山田 太郎",
        "ORG-001",
        "リテール開発部 第一課",
        LeaveType.PAID,
        new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3)),
        "私用",
        LeaveStatus.PENDING,
        Instant.parse("2026-05-20T09:00:00Z"),
        null,
        null,
        null,
        null);
  }

  /**
   * approver / appConfig を組み合わせた Service を構築する.
   *
   * <p>早期 throw する経路では appConfig stub が呼ばれないため lenient() で宣言する。 sysLogger は CDI で
   * 注入されないため、同パッケージ可視性を利用して直接代入する。
   */
  private ApproveLeaveService newService() {
    lenient().when(appConfig.getManagerLayerCodes()).thenReturn(Set.of("M1", "M2"));
    return newServiceWithContext(approver);
  }

  /**
   * 指定したユーザコンテキストで Service を構築し sysLogger に mock を差し込む.
   *
   * @param userInfoContext 承認者として動作させるユーザコンテキスト
   * @return sysLogger 注入済みの ApproveLeaveService
   */
  private ApproveLeaveService newServiceWithContext(final UserInfoContext userInfoContext) {
    final ApproveLeaveService svc =
        new ApproveLeaveService(
            repository, clock, userInfoContext, new ApprovalPolicy(), appConfig);
    svc.sysLogger = sysLogger;
    return svc;
  }

  /**
   * 正常系：PENDING の申請が APPROVED に遷移し、判断者 snapshot が保存される.
   *
   * <p>snapshot 5 項目の検証は単一観点なので UnitTestContainsTooManyAsserts を抑制。
   */
  @Test
  void approvesPendingRequestSuccessfully() {
    when(repository.findById(10L)).thenReturn(Optional.of(pendingByE0001()));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final ApproveLeaveService svc = newService();
    svc.approve(ApproveLeaveCommand.builder().leaveRequestId(10L).comment("承認します").build());

    final ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
    verify(repository).save(captor.capture());
    final LeaveRequest saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    assertThat(saved.getJudgeEmpNum()).isEqualTo("E0002");
    assertThat(saved.getJudgeName()).isEqualTo("佐藤 花子");
    assertThat(saved.getJudgedAt()).isEqualTo(Instant.parse("2026-05-21T10:00:00Z"));
    assertThat(saved.getJudgeComment()).isEqualTo("承認します");
  }

  /** 異常系：対象 ID の申請が存在しない場合 MSTBusinessException を投げる（BR-PRE-001）. */
  @Test
  void rejectsWhenTargetNotFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    final ApproveLeaveService svc = newService();
    assertThatThrownBy(
            () ->
                svc.approve(
                    ApproveLeaveCommand.builder().leaveRequestId(99L).comment(null).build()))
        .isInstanceOf(MSTBusinessException.class);
  }

  /** 異常系：既に APPROVED 済みの申請に対して再承認しようとした場合は拒否（BR-PRE-002）. */
  @Test
  void rejectsWhenAlreadyApproved() {
    final LeaveRequest alreadyApproved = pendingByE0001();
    alreadyApproved.approve("E0002", "佐藤 花子", Instant.parse("2026-05-21T10:00:00Z"), null);
    when(repository.findById(10L)).thenReturn(Optional.of(alreadyApproved));

    final ApproveLeaveService svc = newService();
    assertThatThrownBy(() -> svc.approve(ApproveLeaveCommand.builder().leaveRequestId(10L).build()))
        .isInstanceOf(MSTBusinessException.class);
  }

  /** 異常系：申請者と承認者の社員番号が同一（自己承認）の場合は拒否（BR-PRE-005）. */
  @Test
  void rejectsSelfApproval() {
    final LeaveRequest selfPending = pendingByE0001();
    // approver と申請者が同一社員番号 E0001 のスタブ。
    final UserInfoContext selfContext =
        UserInfoContextFactory.create("E0001", "山田 太郎", "ORG-001", "リテール開発部 第一課", "M1");
    when(repository.findById(10L)).thenReturn(Optional.of(selfPending));
    // 自己承認チェックが先に発火するため appConfig.getManagerLayerCodes() は呼ばれない。lenient() で宣言。
    lenient().when(appConfig.getManagerLayerCodes()).thenReturn(Set.of("M1", "M2"));
    final ApproveLeaveService svc = newServiceWithContext(selfContext);

    assertThatThrownBy(() -> svc.approve(ApproveLeaveCommand.builder().leaveRequestId(10L).build()))
        .isInstanceOf(MSTBusinessException.class);
  }

  /** 異常系：承認者の所属 ORG が申請者と異なる場合は ApprovalPolicy で拒否（BR-POL-001）. */
  @Test
  void rejectsWhenApproverNotAuthorized() {
    final LeaveRequest pending = pendingByE0001();
    final UserInfoContext diffOrg =
        UserInfoContextFactory.create("E0003", "鈴木 次郎", "ORG-002", "コーポレート開発部", "M1");
    when(repository.findById(10L)).thenReturn(Optional.of(pending));
    when(appConfig.getManagerLayerCodes()).thenReturn(Set.of("M1", "M2"));
    final ApproveLeaveService svc = newServiceWithContext(diffOrg);

    assertThatThrownBy(() -> svc.approve(ApproveLeaveCommand.builder().leaveRequestId(10L).build()))
        .isInstanceOf(MSTBusinessException.class);
  }
}
