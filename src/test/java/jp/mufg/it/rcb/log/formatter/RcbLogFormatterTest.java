package jp.mufg.it.rcb.log.formatter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// PMD 抑制理由:
//  - TooManyMethods: フォーマット仕様（メッセージ解決/MDC/Level マップ/Throwable など）の観点別検証で
//    メソッド数が増えるが、各テストが独立観点を持つため分割せず明示抑制する。
/**
 * {@link RcbLogFormatter} の振る舞いを検証する.
 *
 * <p>MDC 状態と {@code System.err} を各テストで隔離して、未解決警告とフォーマット仕様を確認する。
 */
@SuppressWarnings("PMD.TooManyMethods")
class RcbLogFormatterTest {

  /** テスト対象 Formatter. */
  private RcbLogFormatter formatter;

  /** 元の {@code System.err}（テスト後に復元するため保持）. */
  private PrintStream originalErr;

  /** {@code System.err} 差替え用バッファ. */
  private ByteArrayOutputStream errBuffer;

  /** デフォルトコンストラクタ（テストクラスは状態を持たないため初期化処理なし）. */
  /* default */ RcbLogFormatterTest() {
    // 状態を持たないため初期化処理は不要。
  }

  /** 各テスト前に Formatter インスタンスと {@code System.err} を初期化する. */
  @BeforeEach
  void setUp() {
    formatter = new RcbLogFormatter();
    originalErr = System.err;
    errBuffer = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
    MDC.remove("requestId");
    MDC.remove("empNum");
  }

  /** 各テスト後に {@code System.err} と MDC を元に戻す. */
  @AfterEach
  void tearDown() {
    System.setErr(originalErr);
    MDC.remove("requestId");
    MDC.remove("empNum");
  }

  /** 登録済みメッセージ ID＋パラメータ → テンプレートが解決され MessageFormat 展開される. */
  @Test
  void formatResolvesMessageIdAndExpandsParameters() {
    final LogRecord record = newRecord(Level.INFO, "RCB00001-I", 42L);
    final String output = formatter.format(record);
    assertTrue(output.contains("タスクを作成しました"));
    assertTrue(output.contains("42"));
    assertFalse(output.contains("RCB00001-I"));
  }

  /** 未登録メッセージ ID → 元キーがそのまま出力され、System.err に警告が出る. */
  @Test
  void formatFallsBackToRawKeyForUnknownId() {
    final String unknown = "RCB99002-X-formatter-test";
    final LogRecord record = newRecord(Level.WARNING, unknown);
    final String output = formatter.format(record);
    assertTrue(output.contains(unknown));
    final String captured = errBuffer.toString(StandardCharsets.UTF_8);
    assertTrue(captured.contains(unknown));
    assertTrue(captured.contains("unresolved messageId"));
  }

  /** FINE 以下用の生メッセージ（RCB/MST 以外の先頭） → 解決を試みずそのまま出す. */
  @Test
  void formatPassesThroughRawMessage() {
    final LogRecord record = newRecord(Level.FINE, "プレーンテキストのメッセージ");
    final String output = formatter.format(record);
    assertTrue(output.contains("プレーンテキストのメッセージ"));
    assertTrue(errBuffer.toString(StandardCharsets.UTF_8).isEmpty());
  }

  /** MDC が設定されていれば出力に {@code [requestId][empNum]} 形式で含まれる. */
  @Test
  void formatIncludesMdcValues() {
    MDC.put("requestId", "abcd1234");
    MDC.put("empNum", "E0001");
    final LogRecord record = newRecord(Level.INFO, "RCB00002-I", "E0001");
    final String output = formatter.format(record);
    assertTrue(output.contains("[abcd1234][E0001]"));
  }

  /** MDC 未設定時はプレースホルダ {@code -} を使う. */
  @Test
  void formatUsesPlaceholderWhenMdcAbsent() {
    final LogRecord record = newRecord(Level.INFO, "RCB00001-I", 1L);
    final String output = formatter.format(record);
    assertTrue(output.contains("[-][-]"));
  }

  /** Level.WARNING は出力上 {@code WARN } に短縮される（WildFly pattern と整合）. */
  @Test
  void formatMapsWarningLevelToWarn() {
    final LogRecord record = newRecord(Level.WARNING, "RCB00002-I");
    final String output = formatter.format(record);
    assertTrue(output.contains(" WARN  ["));
    assertFalse(output.contains("WARNING"));
  }

  /** Throwable 付きレコードはスタックトレースが本文後ろに付与される. */
  @Test
  void formatAppendsStackTraceForThrowable() {
    final LogRecord record = newRecord(Level.SEVERE, "RCB00001-E");
    record.setThrown(new IllegalStateException("test failure"));
    final String output = formatter.format(record);
    assertTrue(output.contains("IllegalStateException"));
    assertTrue(output.contains("test failure"));
  }

  /** SEVERE は 6 文字なのでパディングなしでそのまま出力される. */
  @Test
  void formatHandlesLongLevelName() {
    final LogRecord record = newRecord(Level.SEVERE, "RCB00001-E");
    final String output = formatter.format(record);
    assertTrue(output.contains(" SEVERE ["));
  }

  /** ログレコード生成のヘルパー. */
  private static LogRecord newRecord(
      final Level level, final String message, final Object... params) {
    final LogRecord record = new LogRecord(level, message);
    record.setLoggerName("test.logger");
    if (params.length > 0) {
      record.setParameters(params);
    }
    return record;
  }
}
