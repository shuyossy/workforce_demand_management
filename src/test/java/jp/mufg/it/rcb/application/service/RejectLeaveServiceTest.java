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
import jp.mufg.it.rcb.application.port.in.RejectLeaveCommand;
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

/** RejectLeaveService の単体テスト. */
@ExtendWith(MockitoExtension.class)
class RejectLeaveServiceTest {

  /** 永続化 port のモック. */
  @Mock private LeaveRepositoryPort repository;

  /** AppConfig のモック（MicroProfile Config 経由の解決を回避するため）. */
  @Mock private AppConfig appConfig;

  /** Logger のモック（@InjectLogger による CDI 注入がテスト環境では行われないため）. */
  @Mock private Logger sysLogger;

  /** 固定時刻クロック（判断日時の検証用）. */
  private final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-05-21T11:00:00Z"));

  /** 却下者のユーザコンテキスト（同一 ORG / 部長層 M1）. */
  private final UserInfoContext approver =
      UserInfoContextFactory.create("E0002", "佐藤 花子", "ORG-001", "リテール開発部 第一課", "M1");

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ RejectLeaveServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * E0001 が申請した PENDING の休暇申請を新規生成する.
   *
   * <p>reject() が状態を mutate するため、テストごとに新しいインスタンスを返す必要がある。
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
  private RejectLeaveService newService() {
    lenient().when(appConfig.getManagerLayerCodes()).thenReturn(Set.of("M1", "M2"));
    final RejectLeaveService svc =
        new RejectLeaveService(repository, clock, approver, new ApprovalPolicy(), appConfig);
    svc.sysLogger = sysLogger;
    return svc;
  }

  /**
   * 正常系：PENDING の申請が REJECTED に遷移し、判断コメントが保存される.
   *
   * <p>status と judgeComment の 2 観点を一括検証するため UnitTestContainsTooManyAsserts を抑制。
   */
  @Test
  void rejectsPendingRequestWithComment() {
    when(repository.findById(10L)).thenReturn(Optional.of(pendingByE0001()));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    final RejectLeaveService svc = newService();
    svc.reject(RejectLeaveCommand.builder().leaveRequestId(10L).comment("期間調整が必要").build());

    final ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
    verify(repository).save(captor.capture());
    final LeaveRequest saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(LeaveStatus.REJECTED);
    assertThat(saved.getJudgeComment()).isEqualTo("期間調整が必要");
  }

  /** 異常系：既に REJECTED 済みの申請に対して再却下しようとした場合は拒否（BR-PRE-002）. */
  @Test
  void rejectsWhenAlreadyRejected() {
    final LeaveRequest alreadyRejected = pendingByE0001();
    alreadyRejected.reject("E0002", "佐藤 花子", Instant.parse("2026-05-21T10:00:00Z"), "理由");
    when(repository.findById(10L)).thenReturn(Optional.of(alreadyRejected));

    final RejectLeaveService svc = newService();
    assertThatThrownBy(
            () ->
                svc.reject(
                    RejectLeaveCommand.builder().leaveRequestId(10L).comment("別の理由").build()))
        .isInstanceOf(MSTBusinessException.class);
  }

  /** 異常系：対象 ID の申請が存在しない場合は MSTBusinessException を投げる. */
  @Test
  void rejectsWhenNotFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    final RejectLeaveService svc = newService();
    assertThatThrownBy(
            () ->
                svc.reject(RejectLeaveCommand.builder().leaveRequestId(99L).comment("理由").build()))
        .isInstanceOf(MSTBusinessException.class);
  }

  /** 異常系：申請者と判断者が同一人物の場合は MSTBusinessException を投げる（BR-PRE-004 自己却下不可）. */
  @Test
  void rejectsWhenSelfRequest() {
    when(repository.findById(10L)).thenReturn(Optional.of(pendingByE0001()));
    final RejectLeaveService svc =
        new RejectLeaveService(
            repository,
            clock,
            UserInfoContextFactory.create("E0001", "山田 太郎", "ORG-001", "リテール開発部 第一課", "M1"),
            new ApprovalPolicy(),
            appConfig);
    svc.sysLogger = sysLogger;

    assertThatThrownBy(
            () ->
                svc.reject(RejectLeaveCommand.builder().leaveRequestId(10L).comment("理由").build()))
        .isInstanceOf(MSTBusinessException.class);
  }

  /** 異常系：判断者が承認権限を持たない場合は MSTBusinessException を投げる（BR-POL-001 違反）. */
  @Test
  void rejectsWhenJudgeNotAuthorized() {
    when(repository.findById(10L)).thenReturn(Optional.of(pendingByE0001()));
    when(appConfig.getManagerLayerCodes()).thenReturn(Set.of("M1", "M2"));
    final RejectLeaveService svc =
        new RejectLeaveService(
            repository,
            clock,
            // 同 ORG だが非部長層（L1）のため権限なし
            UserInfoContextFactory.create("E0002", "佐藤 花子", "ORG-001", "リテール開発部 第一課", "L1"),
            new ApprovalPolicy(),
            appConfig);
    svc.sysLogger = sysLogger;

    assertThatThrownBy(
            () ->
                svc.reject(RejectLeaveCommand.builder().leaveRequestId(10L).comment("理由").build()))
        .isInstanceOf(MSTBusinessException.class);
  }

  /** 正常系：sysLogger.isLoggable(INFO) が false でもログ出力をスキップして処理を完遂する. */
  @Test
  void rejectsSilentlyWhenLoggerIsNotLoggable() {
    when(repository.findById(10L)).thenReturn(Optional.of(pendingByE0001()));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sysLogger.isLoggable(any())).thenReturn(false);

    final RejectLeaveService svc = newService();
    svc.reject(RejectLeaveCommand.builder().leaveRequestId(10L).comment("理由").build());

    verify(repository).save(any());
  }
}
