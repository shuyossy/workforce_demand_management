package jp.mufg.it.rcb.shared.web;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;
import org.jboss.logging.MDC;

/**
 * アクセスログ用フィルタ. リクエスト 1 件ごとに {@code requestId} を MDC へ注入し、終端で 1 行のアクセスログを INFO で出力する.
 *
 * <p>{@code requestId} は 8 桁の UUID 前置で生成する。認証機能を持たないサンプル構成のため {@code empNum} は MDC に 格納せず、{@code
 * RcbLogFormatter} のプレースホルダ {@code -} に委ねる。終了時は finally 句で MDC を必ず除去し、 スレッド再利用時の汚染を防ぐ。LoggerType
 * はプロジェクト 2 値運用 (SYSTEM / ACCESS) に合わせて SYSTEM を使用。
 */
@WebFilter(filterName = "AccessLogFilter", urlPatterns = "/*")
public class AccessLogFilter implements Filter {

  /** {@code requestId} の桁数（UUID 前置の短縮版）. */
  private static final int REQUEST_ID_LENGTH = 8;

  /** アクセスログ用 Logger（プロジェクトの 2 値運用に合わせて SYSTEM を流用）. */
  @Inject
  @InjectLogger(LoggerType.SYSTEM)
  /* default */ Logger accessLogger;

  /** デフォルトコンストラクタ（Servlet 仕様により public 引数なしが必要）. */
  public AccessLogFilter() {
    // Servlet コンテナがインスタンス化するため初期化処理は不要.
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) request;
    final String requestId = UUID.randomUUID().toString().substring(0, REQUEST_ID_LENGTH);
    final long start = System.currentTimeMillis();
    MDC.put("requestId", requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      final long elapsed = System.currentTimeMillis() - start;
      // PMD GuardLogStatement: 重い String.format を回避するため Level ガードを挟む.
      if (accessLogger.isLoggable(Level.INFO)) {
        accessLogger.info(
            String.format("%s %s elapsed=%dms", req.getMethod(), req.getRequestURI(), elapsed));
      }
      MDC.remove("requestId");
    }
  }
}
