package jp.mufg.it.rcb.shared.web;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import jp.mufg.it.rcb.userinfo.context.UserInfoContext;

/**
 * 認証フィルタ. 未認証セッションでの保護リソース要求を {@code /login.xhtml} へ 302 リダイレクトする.
 *
 * <p>{@code /login.xhtml} 自身やエラーページ、JSF / 静的リソースは認証チェックをスキップして無限リダイレクトを防ぐ。 認証済み判定は {@link
 * UserInfoContext#getUser()} の非 null チェックで行う。
 */
@WebFilter(filterName = "AuthenticationFilter", urlPatterns = "/*")
public class AuthenticationFilter implements Filter {

  /** 認証チェックをスキップするコンテキスト相対パスの先頭一致パターン. */
  private static final List<String> SKIP_PATTERNS =
      List.of(
          "/login.xhtml",
          "/error.xhtml",
          "/jakarta.faces.resource/",
          "/resources/",
          "/javax.faces.resource/");

  /** セッション内ユーザコンテキスト. */
  @Inject /* default */ UserInfoContext userInfoContext;

  /** デフォルトコンストラクタ（Servlet 仕様により public 引数なしが必要）. */
  public AuthenticationFilter() {
    // Servlet コンテナがインスタンス化するため初期化処理は不要.
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) request;
    final HttpServletResponse res = (HttpServletResponse) response;
    final String uri = req.getRequestURI();
    final String contextPath = req.getContextPath();
    final String relative = uri.substring(contextPath.length());
    final boolean skip = SKIP_PATTERNS.stream().anyMatch(relative::startsWith);
    if (skip || userInfoContext.getUser() != null) {
      chain.doFilter(request, response);
      return;
    }
    res.sendRedirect(contextPath + "/login.xhtml");
  }
}
