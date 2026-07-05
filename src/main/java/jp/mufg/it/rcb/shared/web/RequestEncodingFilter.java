package jp.mufg.it.rcb.shared.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import java.io.IOException;

/**
 * リクエスト / レスポンスの文字エンコーディングを UTF-8 に固定するフィルタ.
 *
 * <p>Servlet 仕様では {@code application/x-www-form-urlencoded} な POST ボディの文字エンコーディングは {@code
 * ServletRequest#setCharacterEncoding(String)} が明示的に呼ばれない限り既定で ISO-8859-1 として
 * 解釈される。本フィルタが無いと、日本語を含むフォーム入力（タスクのタイトル等）が文字化けした状態で 永続化されてしまうため、パラメータ読み取り（{@code getParameter}
 * 系呼び出し）より前にリクエストの 文字エンコーディングを明示的に設定する必要がある。{@code urlPatterns = "/*"} により、パラメータを 読み取る {@code
 * FacesServlet} より必ず前段で実行される。
 */
@WebFilter(filterName = "RequestEncodingFilter", urlPatterns = "/*")
public class RequestEncodingFilter implements Filter {

  /** アプリ全体で統一する文字エンコーディング. */
  private static final String ENCODING = "UTF-8";

  /** デフォルトコンストラクタ（Servlet 仕様により public 引数なしが必要）. */
  public RequestEncodingFilter() {
    // Servlet コンテナがインスタンス化するため初期化処理は不要.
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    request.setCharacterEncoding(ENCODING);
    response.setCharacterEncoding(ENCODING);
    chain.doFilter(request, response);
  }
}
