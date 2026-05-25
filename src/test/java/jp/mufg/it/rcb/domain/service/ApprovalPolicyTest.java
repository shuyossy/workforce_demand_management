package jp.mufg.it.rcb.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** ApprovalPolicy ドメインサービスの単体テスト. */
class ApprovalPolicyTest {

  /** 判定対象の ApprovalPolicy（純粋関数）. */
  private final ApprovalPolicy policy = new ApprovalPolicy();

  /** 部長層レイヤーコード集合のテストフィクスチャ. */
  private final Set<String> managerLayers = Set.of("M1", "M2");

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ ApprovalPolicyTest() {
    // フィクスチャはフィールド初期化子で組み立てるため初期化処理は不要。
  }

  /** 同じ ORG かつ部長層レイヤーなら承認可能であることを確認する. */
  @Test
  void approverInSameOrgAndManagerLayerIsAllowed() {
    final boolean allowed = policy.canApprove("ORG-001", "ORG-001", "M1", managerLayers);
    assertThat(allowed).isTrue();
  }

  /** 同じ ORG でも非部長層レイヤーなら承認不可であることを確認する. */
  @Test
  void approverInSameOrgButNonManagerLayerIsNotAllowed() {
    final boolean allowed = policy.canApprove("ORG-001", "ORG-001", "L1", managerLayers);
    assertThat(allowed).isFalse();
  }

  /** 異なる ORG では部長層レイヤーでも承認不可であることを確認する. */
  @Test
  void approverInDifferentOrgWithManagerLayerIsNotAllowed() {
    final boolean allowed = policy.canApprove("ORG-001", "ORG-002", "M1", managerLayers);
    assertThat(allowed).isFalse();
  }

  /** 異なる ORG かつ非部長層レイヤーの場合に承認不可であることを確認する. */
  @Test
  void approverInDifferentOrgAndNonManagerLayerIsNotAllowed() {
    final boolean allowed = policy.canApprove("ORG-001", "ORG-002", "L1", managerLayers);
    assertThat(allowed).isFalse();
  }

  /** 同じ ORG かつ M2 レイヤーでも承認可能であることを確認する. */
  @Test
  void m2LayerIsAlsoAllowedWhenSameOrg() {
    final boolean allowed = policy.canApprove("ORG-001", "ORG-001", "M2", managerLayers);
    assertThat(allowed).isTrue();
  }

  /** 部長層レイヤー集合が空の場合は全て承認不可であることを確認する. */
  @Test
  void emptyManagerLayerSetRejectsAll() {
    final boolean allowed = policy.canApprove("ORG-001", "ORG-001", "M1", Set.of());
    assertThat(allowed).isFalse();
  }

  /** 承認者の ORG が null なら承認不可であることを確認する. */
  @Test
  void nullApproverOrgIdRejects() {
    final boolean allowed = policy.canApprove("ORG-001", null, "M1", managerLayers);
    assertThat(allowed).isFalse();
  }

  /** 承認者のレイヤーコードが null なら承認不可であることを確認する. */
  @Test
  void nullApproverLayerCodeRejects() {
    final boolean allowed = policy.canApprove("ORG-001", "ORG-001", null, managerLayers);
    assertThat(allowed).isFalse();
  }

  /** 申請者の ORG が null なら承認不可であることを確認する. */
  @Test
  void nullApplicantOrgIdRejects() {
    final boolean allowed = policy.canApprove(null, "ORG-001", "M1", managerLayers);
    assertThat(allowed).isFalse();
  }

  /** 部長層レイヤー集合が null なら承認不可であることを確認する. */
  @Test
  void nullManagerLayerSetRejects() {
    final boolean allowed = policy.canApprove("ORG-001", "ORG-001", "M1", null);
    assertThat(allowed).isFalse();
  }
}
