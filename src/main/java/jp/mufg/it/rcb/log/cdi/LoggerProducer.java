package jp.mufg.it.rcb.log.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loggerの設定および返却を行うProducer。 */
@ApplicationScoped
public class LoggerProducer {

  /**
   * コンストラクタ。
   *
   * @param config ロガー情報
   */
  @Inject
  LoggerProducer() {}

  /**
   * Logger名を指定しない、Loggerの返却を行う。
   *
   * @return Logger
   */
  @Produces
  public Logger getLogger() {
    Logger logger = Logger.getLogger("");
    return logger;
  }

  /**
   * Logger名に対応したLoggerの返却を行う。
   *
   * @param target ターゲット
   * @return Logger
   */
  @Produces
  @InjectLogger(LoggerType.SYSTEM)
  public Logger getLoggerByName(InjectionPoint target) {

    InjectLogger injectLogger = target.getAnnotated().getAnnotation(InjectLogger.class);
    LoggerType loggerType = injectLogger.value();

    String loggerName =
        loggerType.getLoggerTypeStr() + "." + target.getMember().getDeclaringClass().getName();

    Logger logger = Logger.getLogger(loggerName);

    Level logLevel = Level.ALL;

    logger.setLevel(logLevel);
    return logger;
  }
}
