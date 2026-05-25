package jp.mufg.it.rcb.application.port.out;

import java.time.Instant;

// PMD 抑制理由:
//  - ImplicitFunctionalInterface: 時刻取得 port は CDI Bean による実装を前提とし、
//    テスト時には FixedClockStub のような明示クラスへ差し替える運用のため lambda 化を意図しない。
/** 現在時刻の取得を担う出力 port. */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ClockPort {

  /**
   * 現在時刻を取得する.
   *
   * @return 現在時刻（UTC エポックからの瞬間値）
   */
  Instant now();
}
