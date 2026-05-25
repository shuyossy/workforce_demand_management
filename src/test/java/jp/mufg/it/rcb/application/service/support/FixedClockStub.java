package jp.mufg.it.rcb.application.service.support;

import java.time.Instant;
import jp.mufg.it.rcb.application.port.out.ClockPort;

/** テスト用に固定時刻を返す {@link ClockPort} 実装. */
public class FixedClockStub implements ClockPort {

  /** 固定時刻. */
  private final Instant fixed;

  /**
   * 固定時刻を指定して生成する.
   *
   * @param fixed 固定時刻
   */
  public FixedClockStub(final Instant fixed) {
    this.fixed = fixed;
  }

  @Override
  public Instant now() {
    return fixed;
  }
}
