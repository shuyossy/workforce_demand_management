package jp.mufg.it.rcb.log.cdi;

/**
 * Loggerの種類を定義したenum（社内ライブラリ準拠 IF）.
 *
 * <ul>
 *   <li>{@code SYSTEM}: 一般的なシステムロガー（AccessLogFilter 等が利用想定）。
 *   <li>{@code EVENT}: 業務イベントロガー（{@code ExceptionLogHandler} が参照）。
 * </ul>
 */
public enum LoggerType {
  SYSTEM("logger.system"),
  EVENT("logger.event");

  private String loggerType;

  /**
   * コンストラクタ.
   *
   * @param loggerType ロガー種類
   */
  private LoggerType(String loggerType) {
    this.loggerType = loggerType;
  }

  /**
   * loggerTypeのStringを取得する.
   *
   * @return loggerTypeのString
   */
  public String getLoggerTypeStr() {
    return loggerType;
  }
}
