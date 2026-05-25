package jp.mufg.it.rcb.exception.inner;

import jp.mufg.it.rcb.exception.ErrorCode;

/**
 * Mistral業務アプリケーション(JSF)で業務エラー（回復不可）を示す例外クラス.
 *
 * <p><b>オンライン処理に係る設定情報</b>:
 *
 * <ul>
 *   <li>HTTPステータス：500
 *   <li>メッセージ：An error occurred during the business process. [{0}]
 *   <li>メッセージ引数：指定されたパラメーター
 *   <li>基本的に業務エラーメッセージを表示するために使用されます.
 * </ul>
 *
 * <p><b>ログに係る設定情報</b>:
 *
 * <ul>
 *   <li>errorLog(システムログ)：出力します.
 *   <li>eventLog(EHUBログ)：出力します.
 *   <li>ログメッセージ：MST00004-E=An unexpected business error occurred. [{0}]
 * </ul>
 *
 * <p>このクラスは、クラスの継承ツリーが深すぎるという問題（java:S110）を抑制しています.
 *
 * @see ApplicationRuntimeException
 */
@SuppressWarnings("java:S110")
public class MSTBusinessNonRecoverException extends ApplicationRuntimeException {

  /**
   * コンストラクタ.
   *
   * <p>エラーコードは{@link ErrorCode#UNKNOWN_BUSINESS}が適用されます.
   *
   * @param logMessageParams ログメッセージ
   * @param errorMessageParams オンライン画面のエラーメッセージ用パラメーター.
   * @param cause 例外の原因
   */
  public MSTBusinessNonRecoverException(
      String[] logMessageParams, String[] errorMessageParams, Throwable cause) {
    super(
        ErrorCode.UNKNOWN_BUSINESS,
        ErrorCode.UNKNOWN_BUSINESS.getMessageId(),
        logMessageParams,
        errorMessageParams,
        true,
        true,
        cause);
  }

  /**
   * エラーコードを設定するメソッド. httpステータスとメッセージのデフォルト設定を上書きしたい場合にこのメソッドを利用して設定してください.
   *
   * @param errorCode 応答エラーコード.
   * @return 自身のインスタンス.
   */
  public MSTBusinessNonRecoverException overrideErrorCode(ErrorCode errorCode) {
    this.errorCode = errorCode;
    this.logMessageId = errorCode.getMessageId();
    return this;
  }

  /**
   * errorLog(システムログ)の出力設定をoffに設定するメソッド. デフォルトがログ出力する設定ですが、ログを出力したくない場合に利用してください
   *
   * @return 自身のインスタンス.
   */
  public MSTBusinessNonRecoverException turnOffErrorLog() {
    this.errorLogRequired = false;
    return this;
  }

  /**
   * eventLog(EHUBログ)のの出力設定をoffに設定するメソッド. デフォルトがログ出力する設定ですが、ログを出力したくない場合に利用してください
   *
   * @return 自身のインスタンス.
   */
  public MSTBusinessNonRecoverException turnOffEventLog() {
    this.eventLogRequired = false;
    return this;
  }
}
