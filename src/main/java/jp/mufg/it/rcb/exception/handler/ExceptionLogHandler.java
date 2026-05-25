package jp.mufg.it.rcb.exception.handler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.exception.ErrorCode;
import jp.mufg.it.rcb.exception.ExceptionHelper;
import jp.mufg.it.rcb.exception.inner.InnerRuntimeException;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;

/**
 * @brief 例外発生時のログ出力を行うクラス。
 * @details このクラスは、例外が発生した際に、その詳細情報をログに出力します。
 */
@ApplicationScoped
public class ExceptionLogHandler {

  private Logger logger;
  private Logger eventLogger;
  private ExceptionHelper helper;

  /**
   * @brief 引数なしコンストラクタ。
   */
  public ExceptionLogHandler() {}

  /**
   * @brief コンストラクタ。
   * @details このコンストラクタは、システムロガー、イベントロガー、例外処理関連のヘルパーを引数として受け取ります。
   * @param systemLogger システムロガー。
   * @param eventLogger イベントロガー。
   * @param helper 例外処理関連のヘルパー。
   */
  @Inject
  public ExceptionLogHandler(
      @InjectLogger Logger systemLogger,
      @InjectLogger(LoggerType.EVENT) Logger eventLogger,
      ExceptionHelper helper) {
    this.logger = systemLogger;
    this.eventLogger = eventLogger;
    this.helper = helper;
  }

  /**
   * @brief 必要な情報をログ出力する。
   * @details このメソッドは、発生した例外を引数として受け取り、その詳細情報をログに出力します。
   * @param throwable 発生した例外。
   */
  public void handleErrorLog(Throwable throwable) {
    boolean errorLogRequired;
    boolean eventLogRequired;
    String logMessageId;
    String[] logMessageParams;

    InnerRuntimeException innerException = helper.getInnerException(throwable);
    if (innerException != null) {
      errorLogRequired = innerException.isErrorLogRequired();
      eventLogRequired = innerException.isEventLogRequired();
      logMessageId = innerException.getLogMessageId();
      logMessageParams = innerException.getLogMessageParams();
    } else {
      errorLogRequired = true;
      eventLogRequired = true;
      logMessageId = ErrorCode.UNKNOWN.getMessageId();
      logMessageParams = new String[] {};
    }

    if (errorLogRequired) {
      logger.log(Level.SEVERE, logMessageId, logMessageParams);
      helper.logStackTrace(throwable);
    }

    if (eventLogRequired) {
      eventLogger.log(Level.SEVERE, logMessageId, logMessageParams);
    }
  }
}
