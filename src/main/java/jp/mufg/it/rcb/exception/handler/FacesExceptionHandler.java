package jp.mufg.it.rcb.exception.handler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.exception.inner.ApplicationSessionInvalidException;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import lombok.NoArgsConstructor;

/**
 * @Brief JSF用例外処理クラス。 @Details
 * JSFフレームワークとJSFフレームワーク外（Filter）から呼び出される。本クラスで処理できない例外が発生した場合、ラップされたハンドラーへ例外処理を委譲する。
 *
 * @param wrapped ラップ例外ハンドラー
 * @param logHandler 例外ログハンドラー
 * @param responseHandler 例外レスポンスハンドラー
 * @param logger ロガー
 * @return なし
 * @see ExceptionHandlerWrapper
 * @note 未処理の例外イベントが存在する限り、その例外をログに記録し、エラーレスポンスを生成します。
 * @warning エラーページへの遷移を起こすためには、再度例外をthrowする必要があり。
 */
@NoArgsConstructor
@ApplicationScoped
public class FacesExceptionHandler extends ExceptionHandlerWrapper {

  private ExceptionHandler wrapped;
  private ExceptionLogHandler logHandler;
  private ExceptionFacesResponseHandler responseHandler;
  private Logger logger;

  /**
   * @brief コンストラクタ。
   * @details このコンストラクタは、`FacesExceptionHandler`クラスのインスタンスを生成します。
   * @param wrapped ラップ例外ハンドラー。このハンドラーは、`FacesExceptionHandler`が処理できない例外が発生した場合に、その処理を委譲します。
   * @param logHandler 例外ログハンドラー。このハンドラーは、例外が発生した際にその情報をログに出力します。
   * @param responseHandler 例外レスポンスハンドラー。このハンドラーは、例外が発生した際にエラーレスポンスを生成します。
   * @param logger ロガー。このロガーは、ログ出力を行うためのツールです。
   * @return なし
   * @see FacesExceptionHandler
   * @note このコンストラクタは、`@Inject`アノテーションが付けられているため、依存性注入フレームワークによって自動的にインスタンス化されます。
   * @warning
   *     `@SuppressWarnings("deprecation")`アノテーションは、引数なしコンストラクタ側のみdeprecateされており当該コンストラクタはdeprecate対象外。Eclipse上は警告が出るため抑止する。
   */
  @SuppressWarnings("deprecation")
  @Inject
  public FacesExceptionHandler(
      ExceptionHandler wrapped,
      ExceptionLogHandler logHandler,
      ExceptionFacesResponseHandler responseHandler,
      @InjectLogger final Logger logger) {
    this.wrapped = wrapped;
    this.logHandler = logHandler;
    this.responseHandler = responseHandler;
    this.logger = logger;
  }

  @Override
  public ExceptionHandler getWrapped() {
    return wrapped;
  }

  /**
   * @brief 未処理の例外イベントを処理します。
   * @details このメソッドはJSFフレームワークによって自動的に呼び出されます。未処理の例外イベントが存在する限り、
   *     その例外をログに記録し、エラーレスポンスを生成します。そして、処理できない例外が発生した場合には、 ラップされた例外ハンドラに処理を委譲します。
   * @return なし
   * @see ExceptionHandlerWrapper
   * @note 未処理の例外イベントが存在する限り、その例外をログに記録し、エラーレスポンスを生成します。そして、処理できない例外が発生した場合には、
   *     ラップされた例外ハンドラに処理を委譲します。
   * @warning このメソッドはJSFフレームワークによって自動的に呼び出されます。
   */
  @Override
  public void handle() {
    // JSFライフサイクルより、未処理の例外イベントのイテレータを取得
    Iterator<ExceptionQueuedEvent> iterator = getUnhandledExceptionQueuedEvents().iterator();

    // 未処理の例外イベントが存在する限りループ
    while (iterator.hasNext()) {
      // 例外処理開始のログ出力
      logger.log(Level.FINE, "MST05018-I");

      // 次の例外イベントのコンテキストを取得
      ExceptionQueuedEventContext eventContext = iterator.next().getContext();

      // 例外イベントの原因となるThrowableを取得
      Throwable throwable = getRootCause(eventContext.getException());

      try {
        // slimDarwin以外が出す例外でslimDarwinで個別ハンドリングしたい例外はカスタム例外にラップしてスロー
        // ここでラップされなかった例外はslimDarwinで未知エラーとして対応する
        Throwable rootCause = throwable;
        while (rootCause != null
            && rootCause.getCause() != null
            && rootCause != rootCause.getCause()) {
          rootCause = rootCause.getCause();
        }
        // ViewExpiredExceptionの処理
        if (rootCause instanceof ViewExpiredException) {
          throwable = new ApplicationSessionInvalidException(throwable);
        }

        // 例外のログ出力
        logHandler.handleErrorLog(throwable);
        // 例外に対するエラーレスポンスの生成
        responseHandler.handleErrorResponse(throwable, eventContext);
      } catch (IOException e) {
        // 遷移先リソースが存在しない場合のIOExceptionのログ出力
        logger.log(Level.FINE, "MST05020-I");
      } finally {
        // 処理済みの例外イベントの削除とログ出力
        iterator.remove();
        logger.log(Level.FINE, "MST05019-I");
      }
    }
    // このクラスで処理できないJSFライフサイクル内の例外が発生した場合、ラップされた例外ハンドラに処理を委譲
    wrapped.handle();
  }

  /**
   * @brief Filter経由での発生例外のハンドリングを行う。
   * @details EntryFilterにて、例外ハンドリングのためthrowableをcatch後に、当該メソッドが呼び出されることを前提とする。
   * @param throwable ハンドリング対象の発生例外。
   * @param response レスポンスオブジェクト。
   * @return なし
   * @throws RuntimeException エラーページへの遷移。
   * @see EntryFilter
   * @note このメソッドは、例外をログに出力した後、RuntimeExceptionをthrowすることでエラーページへの遷移を起こします。その直後、finally句にて更にログ出力します。
   * @warning ここでスリムDarwin例外を用いても、再度スローした例外はEntryFilterでキャッチされず、かつ、エラーログも出力されない。
   *     ゆえにJavaの標準のRuntimeExceptionをThrowしてサーブレット、web.xmlで例外処理をさせる。
   */
  @SuppressWarnings({"java:S110", "java:S112"})
  public void handle(Throwable throwable, HttpServletResponse response) throws RuntimeException {
    logger.log(Level.FINE, "MST05018-I");

    // try句にてログ出力した後、RuntimeExceptionをthrowすることでエラーページへの遷移を起こす。
    // その直後、finally句にて更にログ出力する。
    try {
      logHandler.handleErrorLog(throwable);
      // web.xmlに指定したエラーページに遷移させるためには、再度例外をthrowする必要がある。
      // なお、ここでスリムDarwin例外を用いても、再度スローした例外はEntryFilterでキャッチされず、かつ、エラーログも出力されない。
      // ゆえにJavaの標準のRuntimeExceptionをThrowしてサーブレット、web.xmlで例外処理をさせる。

      // 元の例外を取得
      Throwable rootCause = throwable;
      while (rootCause != null
          && rootCause.getCause() != null
          && rootCause != rootCause.getCause()) {
        rootCause = rootCause.getCause();
      }
      // rootCauseがRuntimeException配下のインスタンスであることを確認
      if (rootCause instanceof RuntimeException) {
        // そのまま再スロー
        throw (RuntimeException) rootCause;
      } else {
        // rootCauseがRuntimeExceptionでない場合、新しいRuntimeExceptionでラップしてスロー
        // RuntimeExceptionをthrowしたことの通知。
        logger.log(Level.SEVERE, "MST05021-E");
        throw new RuntimeException(rootCause);
      }
    } finally {
      logger.log(Level.FINE, "MST05019-I");
    }
  }
}
