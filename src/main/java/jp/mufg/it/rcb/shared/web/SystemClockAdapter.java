package jp.mufg.it.rcb.shared.web;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import jp.mufg.it.rcb.application.port.out.ClockPort;

/**
 * {@link ClockPort} の本実装. システム時計から現在時刻を取得する CDI Bean.
 *
 * <p>テスト時は固定時刻を返す Stub に差し替える運用とする。
 */
@ApplicationScoped
public class SystemClockAdapter implements ClockPort {

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public SystemClockAdapter() {
    // CDI Bean のため初期化処理は不要.
  }

  @Override
  public Instant now() {
    return Instant.now();
  }
}
