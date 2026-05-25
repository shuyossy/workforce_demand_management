package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jp.mufg.it.rcb.domain.service.ApprovalPolicy;

/**
 * ドメインサービスを CDI 管理 Bean として公開するための Producer.
 *
 * <p>ドメイン層は CDI / Jakarta EE 非依存（ArchUnit `domainPurity` で hard gate）に保つため、 ドメインサービスへの
 * {@code @ApplicationScoped} 直付けを避け、本クラスを application 層側で 介在させて CDI コンテキストへ公開する。
 */
@ApplicationScoped
public class DomainServiceProducer {

  /** デフォルトコンストラクタ（CDI で利用するため明示宣言）. */
  public DomainServiceProducer() {
    // Producer 自身は状態を持たない。
  }

  /**
   * {@link ApprovalPolicy} のインスタンスを供給する.
   *
   * <p>{@link ApprovalPolicy} は {@code final} クラスのため normal scope (例: {@code @ApplicationScoped})
   * では Weld がクライアントプロキシを生成できない (WELD-001437)。状態を持たない純粋関数サービスのため、 各 injection point に対して新規インスタンスを返す
   * pseudo scope {@code @Dependent} で公開する。
   *
   * @return 状態を持たない承認可否判定ドメインサービス
   */
  @Produces
  @Dependent
  public ApprovalPolicy approvalPolicy() {
    return new ApprovalPolicy();
  }
}
