package jp.mufg.it.rcb.log.formatter;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// PMD 抑制理由:
//  - SystemPrintln: Formatter から JUL Logger を呼ぶと無限再帰の恐れがあるため、
//    未解決ID時の警告のみ System.err 直書きで通知する。
/**
 * {@code messages.properties} からメッセージテンプレートを解決するユーティリティ.
 *
 * <p>クラスロード時に 1 度だけ {@link ResourceBundle} を読み込み、以降は同一インスタンスを参照する。 解決失敗（未登録ID）時は同一キーに対して 1 回だけ
 * {@code System.err} に警告を出力する。
 *
 * <p>{@link java.util.logging.Logger} を一切呼ばない設計とする。Formatter 内部の警告から JUL Logger を経由すると {@link
 * RcbLogFormatter#format} が再帰呼び出しされる恐れがあるためである。
 */
@SuppressWarnings("PMD.SystemPrintln")
final class RcbMessageResolver {

  /** {@code messages.properties} のベース名. */
  private static final String BUNDLE_BASE_NAME = "messages";

  /** 解決対象の ResourceBundle（読込失敗時は {@code null}）. */
  private static final ResourceBundle BUNDLE = loadBundle();

  /** 既に警告を出した未解決キー集合（同一キーの警告を 1 回に抑止する）. */
  private static final Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

  /** ユーティリティクラスのためインスタンス化禁止. */
  private RcbMessageResolver() {}

  /**
   * 指定したメッセージ ID に対応するテンプレート文字列を返す.
   *
   * @param key メッセージ ID（例: {@code "RCB00001-I"}）
   * @return 解決成功時はテンプレート、未解決時または引数が {@code null} の場合は {@code null}
   */
  /* default */ static String resolve(final String key) {
    if (BUNDLE == null || key == null) {
      return null;
    }
    try {
      return BUNDLE.getString(key);
    } catch (MissingResourceException e) {
      return null;
    }
  }

  /**
   * 未解決キーに対して 1 回だけ {@code System.err} に警告を出力する.
   *
   * @param key 未解決メッセージ ID
   */
  /* default */ static void warnUnresolvedOnce(final String key) {
    if (key != null && WARNED_KEYS.add(key)) {
      System.err.println("[RcbLogFormatter] unresolved messageId=" + key);
    }
  }

  /** 起動時 1 回だけ ResourceBundle を読み込む. 読込失敗時は警告のみ出して {@code null} を返す. */
  private static ResourceBundle loadBundle() {
    try {
      return ResourceBundle.getBundle(
          BUNDLE_BASE_NAME, Locale.ROOT, Thread.currentThread().getContextClassLoader());
    } catch (MissingResourceException e) {
      System.err.println("[RcbLogFormatter] messages.properties not found: " + e.getMessage());
      return null;
    }
  }
}
