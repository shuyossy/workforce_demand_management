package jp.mufg.it.rcb.exception.inner;

import jp.mufg.it.rcb.exception.ErrorCode;

/**
 * 認証がセッションが無効になったことを示す例外クラス。
 *
 * <p>本例外は、例外処理機能でのエラーログ出力およびイベントログ出力対象外。
 */
@SuppressWarnings("java:S110")
public class ApplicationSessionInvalidException extends ApplicationRuntimeException {

  /**
   * コンストラクタ。
   *
   * @param logMessageParams ログ用パラメーター。
   */
  public ApplicationSessionInvalidException(Throwable cause) {
    super(
        ErrorCode.APPLICATION_SESSION_INVALID,
        ErrorCode.APPLICATION_SESSION_INVALID.getMessageId(),
        null,
        null,
        false,
        false,
        cause);
  }
}
