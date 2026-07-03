package jp.mufg.it.rcb.shared.config;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * アプリ全体で参照する設定値を MicroProfile Config 経由で公開する CDI Bean.
 *
 * <p>WildFly 32 が同梱する SmallRye Config が実装を提供する。テスト時は Mockito でモックする。 現時点では公開する設定値がないため骨格のみ。今後
 * {@code app.*} 設定を追加する際は本クラスにフィールド・メソッドを追加する。
 */
@ApplicationScoped
public class AppConfig {

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public AppConfig() {
    // CDI Bean のため初期化処理は不要（@ConfigProperty は post-construct で解決される）。
  }
}
