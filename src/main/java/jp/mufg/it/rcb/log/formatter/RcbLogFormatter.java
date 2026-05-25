package jp.mufg.it.rcb.log.formatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jboss.logging.MDC;

/**
 * {@code messages.properties} 解決と MDC 反映を行う社内ライブラリ準拠の JUL {@link Formatter}.
 *
 * <p>出力フォーマット（WildFly subsystem=logging の pattern と等価）:
 *
 * <pre>
 * yyyy-MM-dd'T'HH:mm:ss.SSS LEVEL [loggerName] [requestId][empNum] message[\nstackTrace]
 * </pre>
 *
 * <p>{@link LogRecord#getMessage()} が {@code "RCB"} または {@code "MST"} で始まる場合は メッセージ ID とみなし、{@link
 * RcbMessageResolver#resolve(String)} で {@code messages.properties} から テンプレートを取得して {@link
 * LogRecord#getParameters()} を {@link MessageFormat} で展開する。 解決できない場合は元キーをそのまま出力（fail-open）し、{@link
 * RcbMessageResolver#warnUnresolvedOnce(String)} で 1 回だけ {@code System.err} に警告する。
 *
 * <p>FINE 以下用の素のメッセージ（{@code RCB}/{@code MST} で始まらない文字列）はテンプレート解決を行わず素通しする。
 */
public final class RcbLogFormatter extends Formatter {

  /** タイムスタンプフォーマッタ（システムデフォルトタイムゾーンで ISO 拡張＋ミリ秒）. */
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT)
          .withZone(ZoneId.systemDefault());

  /** 行末区切り（出力プラットフォーム非依存で固定）. */
  private static final String LINE_SEPARATOR = "\n";

  /** メッセージ ID プレフィックス（社内ライブラリ規約: {@code RCB}, {@code MST}）. */
  private static final String[] MESSAGE_ID_PREFIXES = {"RCB", "MST"};

  /** MDC 未設定時のプレースホルダ. */
  private static final String MDC_PLACEHOLDER = "-";

  /** Level 表記幅（WildFly pattern の {@code %-5p} と揃える）. */
  private static final int LEVEL_WIDTH = 5;

  /** デフォルトコンストラクタ（JUL 仕様により public 引数なしが必要）. */
  public RcbLogFormatter() {
    super();
  }

  @Override
  public String format(final LogRecord record) {
    final StringBuilder sb = new StringBuilder(256);
    sb.append(TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(record.getMillis())))
        .append(' ')
        .append(padLevel(record.getLevel()))
        .append(" [")
        .append(record.getLoggerName())
        .append("] [")
        .append(mdcOrDash("requestId"))
        .append("][")
        .append(mdcOrDash("empNum"))
        .append("] ")
        .append(resolveMessage(record))
        .append(LINE_SEPARATOR);
    if (record.getThrown() != null) {
      sb.append(stackTrace(record.getThrown()));
    }
    return sb.toString();
  }

  /** メッセージ ID 解決＋パラメータ展開を行う. */
  private static String resolveMessage(final LogRecord record) {
    final String raw = record.getMessage();
    if (raw == null) {
      return "";
    }
    if (looksLikeMessageId(raw)) {
      final String template = RcbMessageResolver.resolve(raw);
      if (template == null) {
        RcbMessageResolver.warnUnresolvedOnce(raw);
        return formatTemplate(raw, record.getParameters());
      }
      return formatTemplate(template, record.getParameters());
    }
    return formatTemplate(raw, record.getParameters());
  }

  /**
   * {@link MessageFormat} でパラメータを展開する. パラメータ無しなら template をそのまま返す。 フォーマット失敗時もテンプレートを返してログ出力自体は継続する。
   */
  private static String formatTemplate(final String template, final Object... params) {
    if (params == null || params.length == 0) {
      return template;
    }
    try {
      return MessageFormat.format(template, params);
    } catch (IllegalArgumentException e) {
      return template;
    }
  }

  /** メッセージ ID らしい文字列か判定する（プレフィックス先頭一致）. */
  private static boolean looksLikeMessageId(final String raw) {
    for (final String prefix : MESSAGE_ID_PREFIXES) {
      if (raw.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /** Level 名を {@link #LEVEL_WIDTH} 桁にパディングする. JUL の {@code WARNING} は {@code WARN} にマップする. */
  private static String padLevel(final Level level) {
    final String name = level == Level.WARNING ? "WARN" : level.getName();
    if (name.length() >= LEVEL_WIDTH) {
      return name;
    }
    final StringBuilder sb = new StringBuilder(LEVEL_WIDTH);
    sb.append(name);
    while (sb.length() < LEVEL_WIDTH) {
      sb.append(' ');
    }
    return sb.toString();
  }

  /** MDC から値を取得し、未設定なら {@link #MDC_PLACEHOLDER} を返す. */
  private static String mdcOrDash(final String key) {
    final Object value = MDC.get(key);
    return value == null ? MDC_PLACEHOLDER : value.toString();
  }

  /** スタックトレースを文字列化する. */
  private static String stackTrace(final Throwable thrown) {
    final StringWriter sw = new StringWriter();
    try (PrintWriter pw = new PrintWriter(sw)) {
      thrown.printStackTrace(pw);
      pw.flush();
    }
    return sw.toString();
  }
}
