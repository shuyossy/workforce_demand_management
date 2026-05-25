package jp.mufg.it.rcb.log.formatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RcbMessageResolver} の振る舞いを検証する.
 *
 * <p>未解決警告の出力は {@code System.err} を差し替えて捕捉する。
 */
class RcbMessageResolverTest {

  /** 元の {@code System.err}（テスト後に復元するため保持）. */
  private PrintStream originalErr;

  /** {@code System.err} 差替え用バッファ. */
  private ByteArrayOutputStream errBuffer;

  /** デフォルトコンストラクタ（テストクラスは状態を持たないため初期化処理なし）. */
  /* default */ RcbMessageResolverTest() {
    // 状態を持たないため初期化処理は不要。
  }

  /** 各テスト前に {@code System.err} をバッファに差し替える. */
  @BeforeEach
  void replaceSystemErr() {
    originalErr = System.err;
    errBuffer = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
  }

  /** 各テスト後に {@code System.err} を元に戻す. */
  @AfterEach
  void restoreSystemErr() {
    System.setErr(originalErr);
  }

  /** messages.properties に登録されたキーは対応するテンプレート文字列を返す. */
  @Test
  void resolveReturnsTemplateForExistingKey() {
    final String template = RcbMessageResolver.resolve("RCB00001-I");
    assertNotNull(template);
    assertTrue(template.contains("{0}"));
  }

  /** 未登録キーは null を返す. */
  @Test
  void resolveReturnsNullForMissingKey() {
    assertNull(RcbMessageResolver.resolve("RCB99999-X"));
  }

  /** null キーは null を返す（NPE を投げない）. */
  @Test
  void resolveReturnsNullForNullKey() {
    assertNull(RcbMessageResolver.resolve(null));
  }

  /** 同一キーへの警告は System.err に 1 回だけ出力される. */
  @Test
  void warnUnresolvedOncePrintsOnlyOnce() {
    final String key = "RCB99001-X-once-test";
    RcbMessageResolver.warnUnresolvedOnce(key);
    RcbMessageResolver.warnUnresolvedOnce(key);
    RcbMessageResolver.warnUnresolvedOnce(key);
    final String captured = errBuffer.toString(StandardCharsets.UTF_8);
    final int occurrences = countOccurrences(captured, key);
    assertEquals(1, occurrences);
  }

  /** null キーに対しては警告も出さない. */
  @Test
  void warnUnresolvedOnceIgnoresNull() {
    RcbMessageResolver.warnUnresolvedOnce(null);
    assertEquals("", errBuffer.toString(StandardCharsets.UTF_8));
  }

  private static int countOccurrences(final String haystack, final String needle) {
    int count = 0;
    int index = 0;
    while (true) {
      final int found = haystack.indexOf(needle, index);
      if (found == -1) {
        return count;
      }
      count++;
      index = found + needle.length();
    }
  }
}
