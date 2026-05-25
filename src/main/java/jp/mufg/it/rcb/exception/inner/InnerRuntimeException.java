package jp.mufg.it.rcb.exception.inner;

import jp.mufg.it.rcb.config.Config;
import jp.mufg.it.rcb.config.ConfigFactory;
import jp.mufg.it.rcb.exception.ErrorCode;

/** アプリケーション、インフラ例外の親クラス。 */
public class InnerRuntimeException extends RuntimeException {

  /** エラーコードオブジェクト。 */
  protected ErrorCode errorCode;

  /** ログメッセージID。 */
  protected String logMessageId;

  /** ログ用パラメーター。 */
  protected String[] logMessageParams;

  /** 応答エラーメッセージ用パラメーター。 */
  protected String[] errorMessageParams;

  /** エラーログON/OFFフラグ。 */
  protected boolean errorLogRequired;

  /** イベントログON/OFFフラグ。 */
  protected boolean eventLogRequired;

  /** 原因例外。 */
  protected Throwable cause;

  /**
   * コンストラクタ。
   *
   * @param errorCode 応答エラーコード。
   * @param logMessageId ログメッセージID。
   * @param logMessageParams ログ用パラメータ。パラメータ不要の場合はnullまたは空の配列。
   * @param errorMessageParams 応答エラーメッセージ用パラメータ。パラメータ不要の場合はnullまたは空の配列。
   * @param errorLogRequired エラーログON/OFFフラグ。
   * @param eventLogRequired イベントログON/OFFフラグ。
   * @param cause 原因例外。
   */
  public InnerRuntimeException(
      ErrorCode errorCode,
      String logMessageId,
      String[] logMessageParams,
      String[] errorMessageParams,
      boolean errorLogRequired,
      boolean eventLogRequired,
      Throwable cause) {

    this.errorCode = errorCode != null ? errorCode : ErrorCode.UNKNOWN;
    this.logMessageId = logMessageId;
    this.logMessageParams = logMessageParams == null ? new String[] {} : logMessageParams;
    this.errorMessageParams = errorMessageParams == null ? new String[] {} : errorMessageParams;
    this.errorLogRequired = errorLogRequired;
    this.eventLogRequired = eventLogRequired;
    this.cause = cause;
  }

  /**
   * 応答エラーコードを取得する。
   *
   * @return 応答エラーコード。
   */
  public String getErrorCode() {
    return errorCode.getErrorCode();
  }

  /**
   * ログメッセージIDを取得する。 {@link #getMessage()}が返すメッセージは、このIDで設定ファイルに定義されたもの。
   *
   * @return ログメッセージID。
   */
  public String getLogMessageId() {
    return logMessageId;
  }

  /**
   * ログ用パラメーターを取得する。
   *
   * @return ログ用パラメーター。
   */
  public String[] getLogMessageParams() {
    return logMessageParams;
  }

  /**
   * 応答エラーメッセージ用パラメーターを取得する。
   *
   * @return 応答エラーメッセージ用パラメーター。
   */
  public String[] getErrorMessageParams() {
    return errorMessageParams;
  }

  /**
   * エラーログON/OFFフラグを取得する。
   *
   * @return この例外がエラーログの出力対象であればtrue。
   */
  public boolean isErrorLogRequired() {
    return errorLogRequired;
  }

  /**
   * イベントログON/OFFフラグを取得する。
   *
   * @return この例外がイベントログの出力対象であればtrue。
   */
  public boolean isEventLogRequired() {
    return eventLogRequired;
  }

  /**
   * 原因例外を取得する。
   *
   * @return 原因例外。
   */
  @Override
  public Throwable getCause() {
    return cause;
  }

  /**
   * 例外メッセージを取得する。
   *
   * <p>本クラスおよびサブクラスでは、ここで取得するメッセージはログ出力用。 エラー応答用のメッセージは別途、{@link
   * #getErrorCode()}が返すコードと関連付けられたメッセージとして 設定ファイルで定義される。
   *
   * @return 例外メッセージ。
   */
  @Override
  public String getMessage() {
    if (logMessageId == null) {
      return this.getClass().getSimpleName();
    }
    Config defaultConfig = ConfigFactory.getConfigMap().get("messages");
    return defaultConfig.getMessage(logMessageId, logMessageParams);
  }
}
