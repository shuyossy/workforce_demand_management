package jp.mufg.it.rcb.exception;

/** エラーコードとメッセージIDのENUM。 */
public enum ErrorCode {
  // 想定外エラー
  UNKNOWN("unknown", "MST00003-E"),
  // 想定外業務エラー
  UNKNOWN_BUSINESS("unknown-business", "MST00004-E"),
  // 無効なリクエスト
  INVALID_REQUEST("invalid-request", "MST00005-I"),
  // 無効な操作
  INVALID_OPERATION("invalid-operation", "MST00006-I"),
  // ページがない
  PAGE_NOT_FOUND("page-not-found", "MST00007-I"),
  // APIが閉じている
  API_CLOSED("closed", "MST03001-I"),
  // 外部エラー
  EXTERNAL_ERROR("external-error", "MST03003-E"),
  // 外部アプリケーションエラー
  EXTERNAL_APP_ERROR("external-app-error", "MST03004-E"),
  // 外部未処理エラー
  EXTERNAL_UNPROCESSED("external-unprocessed", "MST03005-E"),
  // 不正な引数
  ILLEGAL_ARGUMENT("illegal-argument", "MST04101-E"),
  // ユーザー情報の取得失敗
  AUTHENTICATION_INVALID("authentication-invalid", "MST02001-I"),
  // 認証されていない
  UNAUTHORIZED("unauthorized", "MST04002-I"),
  // バリデーションエラー
  VALIDATION_ERROR("validation-error", "MST05001-E"),
  // 一意キーの重複
  DUPLICATE_UNIQUEKEY("duplicate-unique-key", "MST11002-I"),
  // 設定の読み込みエラー
  LOAD_CONFIG_ERROR("load-config-error", "MST17005-I"),
  // データ変換エラー
  DATA_CONVERSION_FAILED("data-conversion-failed", "MST05002-E"),
  // セッション切れエラー
  APPLICATION_SESSION_INVALID("application-session-invalid", "MST00008-I");

  private String errorCode;
  private String messageId;

  /**
   * コンストラクタ。
   *
   * @param errorCode エラーコード
   * @param messageId メッセージID
   */
  ErrorCode(String errorCode, String messageId) {
    this.errorCode = errorCode;
    this.messageId = messageId;
  }

  /**
   * エラーコードのgetter。
   *
   * @return エラーコード
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * メッセージIDのgetter。
   *
   * @return メッセージID
   */
  public String getMessageId() {
    return messageId;
  }
}
