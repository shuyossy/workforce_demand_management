package jp.mufg.it.rcb.exception.inner;

import jp.mufg.it.rcb.exception.ErrorCode;

/** アプリケーション例外クラス。 */
public class ApplicationRuntimeException extends InnerRuntimeException {

  /**
   * コンストラクタ。
   *
   * @param errorCode 応答エラーコードオブジェクト。
   * @param logMessageId ログメッセージID。
   * @param logMessageParams ログ用パラメーター。
   * @param errorMessageParams 応答エラーメッセージ用パラメーター。
   * @param cause 原因例外。
   */
  public ApplicationRuntimeException(
      ErrorCode errorCode,
      String logMessageId,
      String[] logMessageParams,
      String[] errorMessageParams,
      Throwable cause) {
    this(errorCode, logMessageId, logMessageParams, errorMessageParams, true, true, cause);
  }

  /**
   * コンストラクタ。
   *
   * @param errorCode 応答エラーコード。
   * @param logMessageId ログメッセージID。
   * @param logMessageParams ログ用パラメーター。
   * @param errorMessageParams 応答エラーメッセージ用パラメーター。
   * @param errorLogRequired エラーログON/OFFフラグ。
   * @param eventLogRequired イベントログON/OFFフラグ。
   * @param cause 原因例外。
   */
  public ApplicationRuntimeException(
      ErrorCode errorCode,
      String logMessageId,
      String[] logMessageParams,
      String[] errorMessageParams,
      boolean errorLogRequired,
      boolean eventLogRequired,
      Throwable cause) {
    super(
        errorCode,
        logMessageId,
        logMessageParams,
        errorMessageParams,
        errorLogRequired,
        eventLogRequired,
        cause);
  }
}
