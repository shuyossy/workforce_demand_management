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
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import org.jboss.logging.MDC;

/**
 * アクセスログ用フィルタ. リクエスト 1 件ごとに {@code requestId} / {@code empNum} を MDC へ注入し、終端で 1 行のアクセスログを INFO
 * で出力する.
 *
 * <p>{@code requestId} は 8 桁の UUID 前置で生成、{@code empNum} は未認証時 "-" を入れる。 終了時は finally 句で MDC
 * を必ず除去し、スレッド再利用時の汚染を防ぐ。 LoggerType はプロジェクト 2 値運用 (SYSTEM / EVENT) に合わせて SYSTEM を使用。
 */
@WebFilter(filterName = "AccessLogFilter", urlPatterns = "/*")
public class AccessLogFilter implements Filter {

  /** {@code requestId} の桁数（UUID 前置の短縮版）. */
  private static final int REQUEST_ID_LENGTH = 8;

  /** 未認証時に MDC へ入れる {@code empNum} のプレースホルダ. */
  private static final String EMP_NUM_NONE = "-";

  /** アクセスログ用 Logger（プロジェクトの 2 値運用に合わせて SYSTEM を流用）. */
  @Inject
  @InjectLogger(LoggerType.SYSTEM)
  /* default */ Logger accessLogger;

  /** セッション内ユーザコンテキスト（empNum 取得用）. */
  @Inject /* default */ UserInfoContext userInfoContext;

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
    final UserDto user = userInfoContext.getUser();
    final String empNum = user == null ? EMP_NUM_NONE : user.getEmpNum();
    final long start = System.currentTimeMillis();
    MDC.put("requestId", requestId);
    MDC.put("empNum", empNum);
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
      MDC.remove("empNum");
    }
  }
}
