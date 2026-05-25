package jp.mufg.it.rcb.shared.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * アプリ全体で参照する設定値を MicroProfile Config 経由で公開する CDI Bean.
 *
 * <p>WildFly 32 が同梱する SmallRye Config が実装を提供する。テスト時は Mockito でモックする。
 */
@ApplicationScoped
public class AppConfig {

  /** カンマ区切りの部長層 S コード集合（例: "M1,M2"）. */
  @Inject
  @ConfigProperty(name = "app.approval.manager-layer-codes")
  /* default */ String managerLayerCodesRaw;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public AppConfig() {
    // CDI Bean のため初期化処理は不要（@ConfigProperty は post-construct で解決される）。
  }

  /**
   * 部長層と見なす職層 S コードの集合を返す.
   *
   * <p>未設定または空文字列の場合は空集合を返す（呼び出し側の判定式で必ず false 側にフォールバックする）。
   *
   * @return 部長層 S コード集合
   */
  public Set<String> getManagerLayerCodes() {
    final String raw = managerLayerCodesRaw;
    return (raw == null || raw.isBlank()) ? Set.of() : Set.of(raw.split("\\s*,\\s*"));
  }
}
