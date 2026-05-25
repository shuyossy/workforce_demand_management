package jp.mufg.it.rcb.log.formatter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * アプリケーション起動時に root logger 配下の全 {@link Handler} の Formatter を {@link RcbLogFormatter} に差し替える CDI
 * Bean.
 *
 * <p>WildFly subsystem=logging が登録した ConsoleHandler も含めて差し替えるため、 アプリログ出力は {@code
 * messages.properties} 解決と MDC 反映が一元的に効くようになる。
 *
 * <p>注意: CDI コンテナ初期化前の WildFly 自身のブートログは subsystem=logging 側の pattern-formatter
 * で出力される。アプリログ整流化が目的のため、この差異は許容する。
 */
@ApplicationScoped
public class RcbFormatterInstaller {

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public RcbFormatterInstaller() {
    // CDI Bean のため初期化処理は不要。
  }

  /**
   * アプリ起動完了時のフックで Formatter を差し替える.
   *
   * @param init CDI コンテナから渡される初期化イベント（参照のみ、内容は使用しない）
   */
  /* default */ void onStart(@Observes @Initialized(ApplicationScoped.class) final Object init) {
    final RcbLogFormatter formatter = new RcbLogFormatter();
    final Logger root = Logger.getLogger("");
    for (final Handler handler : root.getHandlers()) {
      if (!(handler.getFormatter() instanceof RcbLogFormatter)) {
        handler.setFormatter(formatter);
      }
    }
  }
}
