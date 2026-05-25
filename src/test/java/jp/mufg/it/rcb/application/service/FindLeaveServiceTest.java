package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import jp.mufg.it.rcb.application.port.in.LeaveRequestDetail;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.application.service.support.UserInfoContextFactory;
import jp.mufg.it.rcb.domain.model.LeavePeriod;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;
import jp.mufg.it.rcb.domain.model.LeaveType;
import jp.mufg.it.rcb.domain.service.ApprovalPolicy;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.shared.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** FindLeaveService の単体テスト. */
@ExtendWith(MockitoExtension.class)
class FindLeaveServiceTest {

  /** 対象休暇申請 ID（複数テストで再利用するため定数化）. */
  private static final long TARGET_ID = 10L;

  /** 申請者の社員番号. */
  private static final String APPLICANT_EMP_NUM = "E0001";

  /** 申請者の氏名. */
  private static final String APPLICANT_NAME = "山田 太郎";

  /** 共通の組織 ID. */
  private static final String ORG_ID = "ORG-001";

  /** 共通の組織名. */
  private static final String ORG_NAME = "リテール開発部 第一課";

  /** 部長層 S コード集合（AppConfig stub 戻り値）. */
  private static final Set<String> MANAGER_LAYERS = Set.of("M1", "M2");

  /** 永続化 port のモック. */
  @Mock private LeaveRepositoryPort repository;

  /** AppConfig のモック（MicroProfile Config 経由の解決を回避するため）. */
  @Mock private AppConfig appConfig;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ FindLeaveServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * E0001 が申請した PENDING の休暇申請を新規生成する.
   *
   * @return 構築済み LeaveRequest
   */
  private LeaveRequest pendingByE0001() {
    return LeaveRequest.reconstruct(
        TARGET_ID,
        APPLICANT_EMP_NUM,
        APPLICANT_NAME,
        ORG_ID,
        ORG_NAME,
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

  /** 正常系：別人の承認者（E0002 / 同 ORG / 部長層 M1）が canApprove=true / canReject=true で返る. */
  @Test
  void returnsDetailWithCanApproveTrueForAuthorizedApprover() {
    when(repository.findById(TARGET_ID)).thenReturn(Optional.of(pendingByE0001()));
    when(appConfig.getManagerLayerCodes()).thenReturn(MANAGER_LAYERS);
    final FindLeaveService svc =
        new FindLeaveService(
            repository,
            UserInfoContextFactory.create("E0002", "佐藤 花子", ORG_ID, ORG_NAME, "M1"),
            new ApprovalPolicy(),
            appConfig);

    final LeaveRequestDetail detail = svc.find(TARGET_ID);

    assertThat(detail.getId()).isEqualTo(TARGET_ID);
    assertThat(detail.isCanApprove()).isTrue();
    assertThat(detail.isCanReject()).isTrue();
  }

  /** 異常系：申請者 == 閲覧者（自己申請）の場合は canApprove=false / canReject=false. */
  @Test
  void returnsDetailWithCanApproveFalseForSelfRequest() {
    when(repository.findById(TARGET_ID)).thenReturn(Optional.of(pendingByE0001()));
    when(appConfig.getManagerLayerCodes()).thenReturn(MANAGER_LAYERS);
    final FindLeaveService svc =
        new FindLeaveService(
            repository,
            UserInfoContextFactory.create(
                APPLICANT_EMP_NUM, APPLICANT_NAME, ORG_ID, ORG_NAME, "M1"),
            new ApprovalPolicy(),
            appConfig);

    final LeaveRequestDetail detail = svc.find(TARGET_ID);

    assertThat(detail.isCanApprove()).isFalse();
    assertThat(detail.isCanReject()).isFalse();
  }

  /** 異常系：対象 ID の申請が存在しない場合は MSTBusinessException を投げる. */
  @Test
  void throwsWhenNotFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    final FindLeaveService svc =
        new FindLeaveService(
            repository,
            UserInfoContextFactory.create(
                APPLICANT_EMP_NUM, APPLICANT_NAME, ORG_ID, ORG_NAME, "L1"),
            new ApprovalPolicy(),
            appConfig);
    assertThatThrownBy(() -> svc.find(99L)).isInstanceOf(MSTBusinessException.class);
  }

  /** 異常系：別人の承認者でも非部長層（L1）の場合は canApprove=false / canReject=false. */
  @Test
  void returnsDetailWithCanApproveFalseForNonManagerLayerApprover() {
    when(repository.findById(TARGET_ID)).thenReturn(Optional.of(pendingByE0001()));
    when(appConfig.getManagerLayerCodes()).thenReturn(MANAGER_LAYERS);
    final FindLeaveService svc =
        new FindLeaveService(
            repository,
            UserInfoContextFactory.create("E0002", "佐藤 花子", ORG_ID, ORG_NAME, "L1"),
            new ApprovalPolicy(),
            appConfig);

    final LeaveRequestDetail detail = svc.find(TARGET_ID);

    assertThat(detail.isCanApprove()).isFalse();
    assertThat(detail.isCanReject()).isFalse();
  }

  /** 異常系：別人の承認者かつ部長層でも、既に承認済（APPROVED）の場合は canApprove=false. */
  @Test
  void returnsDetailWithCanApproveFalseForAlreadyApprovedRequest() {
    final LeaveRequest approved =
        LeaveRequest.reconstruct(
            TARGET_ID,
            APPLICANT_EMP_NUM,
            APPLICANT_NAME,
            ORG_ID,
            ORG_NAME,
            LeaveType.PAID,
            new LeavePeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3)),
            "私用",
            LeaveStatus.APPROVED,
            Instant.parse("2026-05-20T09:00:00Z"),
            "E0002",
            "佐藤 花子",
            Instant.parse("2026-05-21T10:00:00Z"),
            "OK");
    when(repository.findById(TARGET_ID)).thenReturn(Optional.of(approved));
    when(appConfig.getManagerLayerCodes()).thenReturn(MANAGER_LAYERS);
    final FindLeaveService svc =
        new FindLeaveService(
            repository,
            UserInfoContextFactory.create("E0003", "鈴木 一郎", ORG_ID, ORG_NAME, "M1"),
            new ApprovalPolicy(),
            appConfig);

    final LeaveRequestDetail detail = svc.find(TARGET_ID);

    assertThat(detail.isCanApprove()).isFalse();
    assertThat(detail.isCanReject()).isFalse();
  }
}
