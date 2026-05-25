package jp.mufg.it.rcb.exception.inner;

import jp.mufg.it.rcb.exception.ErrorCode;

/**
 * Mistral業務アプリケーション(JSF)で業務エラー（回復可）を示す例外クラス.
 *
 * <p><b>オンライン処理に係る設定情報</b>:
 *
 * <ul>
 *   <li>HTTPステータス：400
 *   <li>メッセージ：Invalid operation. [{0}]
 *   <li>メッセージ引数：指定されたパラメーター
 *   <li>基本的に業務エラーメッセージを表示するために使用されます.
 * </ul>
 *
 * <p><b>ログに係る設定情報</b>:
 *
 * <ul>
 *   <li>errorLog(システムログ)：出力しません.
 *   <li>eventLog(EHUBログ)：出力しません.
 *   <li>ログメッセージ：なし
 * </ul>
 *
 * <p><b>遷移先画面の設定情報</b>:
 *
 * <ul>
 *   <li>デフォルト：null（リダイレクトはしない）
 *   <li>遷移先画面を設定する場合は、{@link #setRedirectPage(String)} メソッドを使用します。
 * </ul>
 *
 * @see ApplicationRuntimeException
 */
@SuppressWarnings("java:S110")
public class MSTBusinessException extends ApplicationRuntimeException {

  // デフォルトはnull(リダイレクトはしない)
  private String redirectPage;

  /**
   * コンストラクタ.
   *
   * <p>エラーコードは{@link ErrorCode#INVALID_OPERATION}が適用されます.
   *
   * @param errorMessageParams オンライン画面のエラーメッセージ用パラメーター
   */
  public MSTBusinessException(String... errorMessageParams) {
    super(ErrorCode.INVALID_OPERATION, null, null, errorMessageParams, false, false, null);
  }

  /**
   * エラーコードを設定するメソッド. httpステータスとメッセージのデフォルト設定を上書きしたい場合にこのメソッドを利用して設定してください.
   *
   * @param errorCode 応答エラーコード
   * @return 自身のインスタンス
   */
  public MSTBusinessException overrideErrorCode(ErrorCode errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  /**
   * httpステータスコードでは色分けできない業務の個別画面に遷移したいときに遷移先画面の相対パスを設定するメソッド. ex)/reports/search
   *
   * @param redirectPage 遷移先画面
   * @return 自身のインスタンス
   */
  public MSTBusinessException setRedirectPage(String redirectPage) {
    this.redirectPage = redirectPage;
    return this;
  }

  /**
   * 遷移先画面を取得するメソッド.
   *
   * @return 遷移先画面
   */
  public String getRedirectPage() {
    return redirectPage;
  }
}
