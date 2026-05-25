package jp.mufg.it.rcb.shared.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * junction-path を考慮した絶対 URL を組み立てる CDI Bean.
 *
 * <p>{@code app.external-base-url} が設定されていればそれを優先し、未設定の場合は {@link FacesContext} 経由で取得した {@link
 * HttpServletRequest}（X-Forwarded-* を信頼） から組み立てる。{@link FacesContext} が無い文脈では相対パスのまま返す。
 *
 * <p>通常の画面リンクは JSF コンポーネントが context 相対を解決するため不要で、 メール本文等の絶対 URL が必要な場面でのみ利用する想定。
 */
@ApplicationScoped
public class AppUrlBuilder {

  /**
   * 外部公開ベース URL（例: "https://app.example.com/rcb"）.
   *
   * <p>未設定や空文字列のケースを扱うため {@link Optional} 型で受ける。SmallRye Config は String 直 inject 時に 空文字列を null
   * 扱いして {@code SRCFG00040} を投げるため、{@code Optional<String>} を採用して null/empty/設定済み を 1 つの API で吸収する。
   */
  @Inject
  @ConfigProperty(name = "app.external-base-url")
  /* default */ Optional<String> externalBaseUrl;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public AppUrlBuilder() {
    // CDI Bean のため初期化処理は不要（@ConfigProperty は post-construct で解決される）.
  }

  /**
   * コンテキスト相対パスを絶対 URL に変換する.
   *
   * @param contextRelativePath コンテキスト相対パス（先頭スラッシュ有無は不問）
   * @return 絶対 URL（{@link FacesContext} が無い場合はコンテキスト相対のまま）
   */
  public String absolute(final String contextRelativePath) {
    final String suffix =
        contextRelativePath.startsWith("/") ? contextRelativePath : "/" + contextRelativePath;

    // externalBaseUrl は @Inject Optional<String> なので CDI 起動後は必ず非 null
    // （未設定値は Optional.empty() で渡る）。null 防御は省略して CC を節約する。
    if (externalBaseUrl.isPresent() && !externalBaseUrl.get().isBlank()) {
      return stripTrailingSlash(externalBaseUrl.get()) + suffix;
    }

    // フォールバック：X-Forwarded-* を信頼した getRequestURL を使う.
    final FacesContext fctx = FacesContext.getCurrentInstance();
    if (fctx == null) {
      // FacesContext が無い場面では相対のまま.
      return suffix;
    }
    final HttpServletRequest req = (HttpServletRequest) fctx.getExternalContext().getRequest();
    final String scheme = req.getScheme();
    final String host = req.getServerName();
    final int port = req.getServerPort();
    final String context = req.getContextPath();
    final String hostPort =
        ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)
            ? host
            : host + ":" + port;
    return scheme + "://" + hostPort + context + suffix;
  }

  /**
   * 末尾スラッシュを除去する.
   *
   * @param value 対象文字列
   * @return 末尾スラッシュを除いた文字列
   */
  private static String stripTrailingSlash(final String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
