package jp.mufg.it.rcb.domain.service;

import java.util.Set;

// Checkstyle 抑制理由:
//  - AbbreviationAsWordInName: パラメータ名 approverLayerSCode は
//    UserPositionDto.layerSCode（職層S コード）と業務用語上の整合性を取るための命名。
/**
 * 承認可否判定のドメインサービス（BR-POL-001）.
 *
 * <p>判定式: applicantOrgId == approverOrgId AND approverLayerSCode ∈ managerLayerCodes
 *
 * <p>UserDto への依存を持たず、必要な値を引数で受け取る純粋関数として実装する。
 */
public final class ApprovalPolicy {

  /** デフォルトコンストラクタ. */
  public ApprovalPolicy() {
    // 状態を持たない純粋関数サービスのため初期化処理は不要。
  }

  /**
   * 承認者が申請者の休暇申請を承認可能か判定する.
   *
   * <p>判定条件: 申請者と承認者が同一 ORG に所属し、かつ承認者の職層S コードが部長層集合に含まれること。
   *
   * @param applicantOrgId 申請者の所属 ORG（null の場合は承認不可）
   * @param approverOrgId 承認者の所属 ORG（null の場合は承認不可）
   * @param approverLayerSCode 承認者の職層S コード（null の場合は承認不可）
   * @param managerLayerCodes 部長層と見なす職層S コードの集合（null または空の場合は承認不可）
   * @return 承認可能なら true、そうでなければ false
   */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public boolean canApprove(
      final String applicantOrgId,
      final String approverOrgId,
      final String approverLayerSCode,
      final Set<String> managerLayerCodes) {
    if (applicantOrgId == null || approverOrgId == null || approverLayerSCode == null) {
      return false;
    }
    if (managerLayerCodes == null || managerLayerCodes.isEmpty()) {
      return false;
    }
    return applicantOrgId.equals(approverOrgId) && managerLayerCodes.contains(approverLayerSCode);
  }
}
