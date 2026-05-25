package jp.mufg.it.rcb.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

/**
 * {@link PropertyId} で指定された設定セットに対応する {@link Config} を CDI 経由で生成する Producer.
 *
 * <p>{@code @PropertyId("aaa")} を付与した注入ポイントには {@code aaa.properties} をロードした {@link Config}
 * が注入される。実体は {@link ConfigFactory#getConfigMap()} に登録されたインスタンスを返す。
 */
@ApplicationScoped
public class ConfigProducer {

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public ConfigProducer() {
    // CDI Bean のため初期化処理は不要.
  }

  /**
   * {@link PropertyId} 付き注入ポイント向けの {@link Config} 生成メソッド.
   *
   * @param injectionPoint 注入ポイント情報
   * @return {@code @PropertyId(value)} に対応する {@link Config} インスタンス
   * @throws IllegalArgumentException {@code value} が {@link ConfigFactory#getConfigMap()} に未登録の場合
   */
  @Produces
  @PropertyId
  public Config produceConfig(final InjectionPoint injectionPoint) {
    final PropertyId annotation = injectionPoint.getAnnotated().getAnnotation(PropertyId.class);
    final String key = (annotation != null) ? annotation.value() : "default";
    final Config config = ConfigFactory.getConfigMap().get(key);
    if (config == null) {
      throw new IllegalArgumentException("Unknown PropertyId value: " + key);
    }
    return config;
  }
}
