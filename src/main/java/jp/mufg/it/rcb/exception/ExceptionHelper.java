package jp.mufg.it.rcb.exception;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.config.Config;
import jp.mufg.it.rcb.config.PropertyId;
import jp.mufg.it.rcb.exception.inner.InnerRuntimeException;
import jp.mufg.it.rcb.log.cdi.InjectLogger;

/**
 * @brief 例外処理関連のヘルパークラス。
 * @details 小さな共通処理を行うメソッドの集合。
 */
@ApplicationScoped
public class ExceptionHelper {

  /**
   * @brief 設定ファイルに定義されたエラーメッセージをエラーコードと対応付けて取得するためのキー。
   */
  private static final String PROPERTY_KEY_FORMAT_ERROR_MESSAGE = "exceptionhandler.%s.message";

  /**
   * @brief 設定ファイルに定義されたHTTPステータスコードをエラーコードと対応付けて取得するためのキー。
   */
  private static final String PROPERTY_KEY_FORMAT_HTTP_STATUS = "exceptionhandler.%s.httpstatus";

  private Logger logger;
  private Config config;

  /**
   * @brief 引数なしコンストラクタ。
   */
  public ExceptionHelper() {}

  /**
   * @brief コンストラクタ。
   * @param config 設定オブジェクト。
   * @param logger ロガー。
   */
  @Inject
  public ExceptionHelper(@PropertyId("default") Config config, @InjectLogger Logger logger) {
    this.config = config;
    this.logger = logger;
  }

  /**
   * @brief 例外スタックトレースをログに出力する。
   * @param e 例外。
   */
  @SuppressWarnings("java:S3457")
  public void logStackTrace(Throwable e) {
    try (StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw); ) {
      e.printStackTrace(pw);
      pw.flush();
      logger.log(Level.SEVERE, "MST05010-E", sw.toString());
    } catch (IOException ioe) {
      // このcatch節は到達不能なので実装なし。
      // 上記try節の中でthrows IOExceptionが宣言されているのはWriterのcloseメソッドだけであり、
      // かつ、StringWriterのcloseメソッドはIOExceptionをthrowしないため。
      // ただし、try節の処理をカスタマイズする場合は、IOExceptionが発生する可能性がないか注意が必要。
    }
  }

  /**
   * @brief 例外に基づいて、応答エラーメッセージを取得する。
   * @details 応答エラーコードと、応答エラーメッセージ用パラメーターに基づいた実装を行い、Exception#getMessageの内容は晒さないように配慮している。
   * @param e スリムDarwin例外。
   * @return 応答エラーメッセージ。
   */
  public String getErrorMessage(InnerRuntimeException e) {
    return getErrorMessage(e.getErrorCode(), e.getErrorMessageParams());
  }

  /**
   * @brief 引数で渡されたエラーコードおよびパラメータに基づいて、応答エラーメッセージを取得する。
   * @details 応答エラーコードと、応答エラーメッセージ用パラメーターに基づいた実装を行い、Exception#getMessageの内容は晒さないように配慮している。
   * @param errorCode エラーコードの文字列表現。
   * @param errorMessageParams メッセージパラメータ。
   * @return 応答エラーメッセージ。
   */
  public String getErrorMessage(String errorCode, String... errorMessageParams) {
    String responseMessagekey = String.format(PROPERTY_KEY_FORMAT_ERROR_MESSAGE, errorCode);
    return config.getMessage(responseMessagekey, errorMessageParams);
  }

  /**
   * @brief 例外に設定されたエラーコードから応答HTTPステータスコードを取得する。
   * @param e スリムDarwin例外。
   * @return 応答HTTPステータスコード。
   */
  public int getHttpStatusCode(InnerRuntimeException e) {
    return config.getInt(String.format(PROPERTY_KEY_FORMAT_HTTP_STATUS, e.getErrorCode()));
  }

  /**
   * @brief 発生した例外が、{@link InnerRuntimeException}のサブクラスである場合に、その例外を取り出す。
   * @details このメソッドは、{jp.mufg.it.inhousecommons.initialize.EntryFilter}等で最終的な例外処理を行う際に利用する。
   *     発生例外がスリムDarwinやアプリケーションによるものかどうかを判定するもので、引数のthrowable自身が {@link
   *     InnerRuntimeException}のサブクラスであるかどうかの判定とは異なる。
   *     この仕様の意図するところは、JBoss7.2において、JAX-RSエンジン内で発生した例外が、その外側にthrowされる際、
   *     JBoss内部の例外にラップされてしまうことへの対応である。
   *     JBossのJAX-RSを想定しこれに対処する実装を行っているが、JBoss以外、あるいはJAX-RS以外の環境ではそれぞれ
   *     コンテナの挙動が異なる可能性があるため、実際には本メソッドの冒頭に記載した通りの仕様になるよう実装をカスタマイズする必要がある。
   * @param throwable 例外処理でcatchした例外
   * @return 発生した例外が{@link InnerRuntimeException}である場合はその例外。そうでなければnull。
   * @throws IllegalArgumentException 引数がnullの場合
   */
  public InnerRuntimeException getInnerException(Throwable throwable) {
    // JBossのJAX-RSエンジンは、内部で発生した例外をラップしてthrowする。
    // したがって、throwable自身がInnerRuntimeExceptionでない場合、そのcauseがInnerRuntimeExceptionであれば、
    // このcauseにセットされている例外が元々throwされた例外とみなし、その例外を返している。
    // Javadocにも記載されている通り、この実装は環境によって変更する必要がある可能性がある。
    if (throwable instanceof InnerRuntimeException innerRuntimeException) {
      return innerRuntimeException;
    } else if (throwable.getCause() instanceof InnerRuntimeException causeInnerRuntimeException) {
      return causeInnerRuntimeException;
    }

    return null;
  }
}
