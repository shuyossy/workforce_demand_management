package jp.mufg.it.rcb.config;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 設定取得用の Factory（社内ライブラリ準拠 IF として独自実装）.
 *
 * <p>社内ライブラリ準拠 IF コードからのみ呼ばれる前提。
 */
public final class ConfigFactory {

  /** 設定セット名から Config への Map. */
  private static final Map<String, Config> CONFIG_MAP = buildConfigMap();

  private ConfigFactory() {
    // ユーティリティクラス
  }

  /**
   * 設定セット名から Config を引ける Map を返す.
   *
   * @return Map（読み取り専用）
   */
  public static Map<String, Config> getConfigMap() {
    return CONFIG_MAP;
  }

  /**
   * 既定の Config を取得する.
   *
   * @return 既定の Config
   */
  public static Config getDefault() {
    return CONFIG_MAP.get("default");
  }

  private static Map<String, Config> buildConfigMap() {
    // {key} に対し {key}.properties をロードする ResourceBundleConfig を登録する。
    // 新規 properties ファイルを追加する場合は、ここに同名キーで登録すること。
    final Map<String, Config> map = new ConcurrentHashMap<>();
    map.put("default", new ResourceBundleConfig("default"));
    map.put("messages", new ResourceBundleConfig("messages"));
    return Collections.unmodifiableMap(map);
  }

  /** ResourceBundle ベースの Config 実装. */
  /* default */ static final class ResourceBundleConfig implements Config {

    /** ResourceBundle のベース名. */
    private final String bundleBaseName;

    /* default */ ResourceBundleConfig(final String bundleBaseName) {
      this.bundleBaseName = bundleBaseName;
    }

    @Override
    public String getMessage(final String messageId, final String... params) {
      final String template = lookup(messageId);
      final String result;
      if (template.isEmpty()) {
        result = messageId;
      } else if (params == null || params.length == 0) {
        result = template;
      } else {
        result = MessageFormat.format(template, (Object[]) params);
      }
      return result;
    }

    @Override
    public int getInt(final String key) {
      final String value = lookup(key);
      int result = 0;
      if (!value.isEmpty()) {
        try {
          result = Integer.parseInt(value.trim());
        } catch (NumberFormatException nfe) {
          result = 0;
        }
      }
      return result;
    }

    private String lookup(final String key) {
      String result;
      try {
        final ResourceBundle bundle = ResourceBundle.getBundle(bundleBaseName);
        if (bundle.containsKey(key)) {
          result = bundle.getString(key);
        } else {
          result = "";
        }
      } catch (MissingResourceException mre) {
        // ResourceBundle 自体が存在しない場合は未定義扱い
        result = "";
      }
      return result;
    }
  }
}
