package jp.mufg.it.rcb.config;

/**
 * 社内ライブラリ向けの設定取得インターフェース（社内ライブラリ準拠 IF として独自実装）.
 *
 * <p>社内ライブラリ準拠 IF コードからのみ呼ばれる前提。
 */
public interface Config {

  /**
   * メッセージIDに対応するメッセージ文字列を取得する.
   *
   * @param messageId メッセージID
   * @param params パラメータ
   * @return 展開後のメッセージ文字列。未定義の場合は messageId 自身
   */
  String getMessage(String messageId, String... params);

  /**
   * キーに対応する整数値を取得する.
   *
   * @param key 設定キー
   * @return 整数値。未定義の場合は 0
   */
  int getInt(String key);
}
