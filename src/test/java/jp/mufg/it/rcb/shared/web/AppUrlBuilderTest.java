package jp.mufg.it.rcb.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * {@link AppUrlBuilder#absolute(String)} の単体テスト.
 *
 * <p>外部公開ベース URL 設定済み / 未設定 + FacesContext 不在 / 未設定 + FacesContext あり の各経路を、 {@link MockedStatic} で
 * {@link FacesContext} を差し替えて検証する。実 WildFly + JSF 経路は E2E（Playwright）で担保する。
 */
class AppUrlBuilderTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ AppUrlBuilderTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * リフレクションで package-private フィールド {@code externalBaseUrl} に値を設定する.
   *
   * <p>本体クラスは MicroProfile Config の {@code SRCFG00040}（空文字列を null として扱う）を回避するため {@code
   * Optional<String>} 型を採用しているので、テスト側も {@link Optional#ofNullable(Object)} で包んで渡す。
   *
   * @param builder 対象 {@link AppUrlBuilder}
   * @param value 設定値（null / 空文字 / 通常 URL のいずれも可）
   */
  private static void setExternalBaseUrl(final AppUrlBuilder builder, final String value) {
    try {
      final Field field = AppUrlBuilder.class.getDeclaredField("externalBaseUrl");
      field.setAccessible(true);
      field.set(builder, Optional.ofNullable(value));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("リフレクション設定に失敗", e);
    }
  }

  @Test
  void prependsExternalBaseUrlWhenConfigured() {
    final AppUrlBuilder builder = new AppUrlBuilder();
    setExternalBaseUrl(builder, "https://app.example.com/rcb");
    assertThat(builder.absolute("/list.xhtml")).isEqualTo("https://app.example.com/rcb/list.xhtml");
  }

  @Test
  void prependsExternalBaseUrlAndAddsLeadingSlashWhenMissing() {
    final AppUrlBuilder builder = new AppUrlBuilder();
    setExternalBaseUrl(builder, "https://app.example.com/rcb");
    assertThat(builder.absolute("list.xhtml")).isEqualTo("https://app.example.com/rcb/list.xhtml");
  }

  @Test
  void stripsTrailingSlashFromExternalBaseUrl() {
    final AppUrlBuilder builder = new AppUrlBuilder();
    setExternalBaseUrl(builder, "https://app.example.com/rcb/");
    assertThat(builder.absolute("/list.xhtml")).isEqualTo("https://app.example.com/rcb/list.xhtml");
  }

  /** externalBaseUrl が null かつ FacesContext 不在の場合、相対パスがそのまま返ることを確認する. */
  @Test
  void returnsRelativeWhenExternalBaseUrlNullAndFacesContextAbsent() {
    final AppUrlBuilder builder = new AppUrlBuilder();
    setExternalBaseUrl(builder, null);

    try (MockedStatic<FacesContext> facesCtxMock = Mockito.mockStatic(FacesContext.class)) {
      facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(null);
      assertThat(builder.absolute("/list.xhtml")).isEqualTo("/list.xhtml");
    }
  }

  /** externalBaseUrl が空文字かつ FacesContext 不在の場合、相対パスがそのまま返ることを確認する. */
  @Test
  void returnsRelativeWhenExternalBaseUrlBlankAndFacesContextAbsent() {
    final AppUrlBuilder builder = new AppUrlBuilder();
    setExternalBaseUrl(builder, "");

    try (MockedStatic<FacesContext> facesCtxMock = Mockito.mockStatic(FacesContext.class)) {
      facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(null);
      assertThat(builder.absolute("list.xhtml")).isEqualTo("/list.xhtml");
    }
  }

  /**
   * MockedStatic で FacesContext を差し替え、指定 scheme/host/port/context の HttpServletRequest を返すように仕込んで
   * absolute() を呼び出す共通ヘルパ.
   */
  private static String invokeWithFakeRequest(
      final AppUrlBuilder builder,
      final String contextRelativePath,
      final String scheme,
      final String host,
      final int port,
      final String contextPath) {
    final HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    when(req.getScheme()).thenReturn(scheme);
    when(req.getServerName()).thenReturn(host);
    when(req.getServerPort()).thenReturn(port);
    when(req.getContextPath()).thenReturn(contextPath);

    final ExternalContext extCtx = Mockito.mock(ExternalContext.class);
    when(extCtx.getRequest()).thenReturn(req);

    final FacesContext fctx = Mockito.mock(FacesContext.class);
    when(fctx.getExternalContext()).thenReturn(extCtx);

    try (MockedStatic<FacesContext> facesCtxMock = Mockito.mockStatic(FacesContext.class)) {
      facesCtxMock.when(FacesContext::getCurrentInstance).thenReturn(fctx);
      return builder.absolute(contextRelativePath);
    }
  }

  /** FacesContext あり + http+8080: host:port が結合される（標準ポート以外）. */
  @Test
  void buildsAbsoluteUrlFromFacesContextWithNonStandardPort() {
    final AppUrlBuilder builder = new AppUrlBuilder();
    setExternalBaseUrl(builder, null);
    final String url =
        invokeWithFakeRequest(builder, "/list.xhtml", "http", "app.example.com", 8080, "/rcb");
    assertThat(url).isEqualTo("http://app.example.com:8080/rcb/list.xhtml");
  }

  /** FacesContext あり + http+80: 標準ポート省略で host のみ. */
  @Test
  void buildsAbsoluteUrlFromFacesContextWithHttpStandardPort() {
    final AppUrlBuilder builder = new AppUrlBuilder();
    setExternalBaseUrl(builder, null);
    final String url =
        invokeWithFakeRequest(builder, "/list.xhtml", "http", "app.example.com", 80, "/rcb");
    assertThat(url).isEqualTo("http://app.example.com/rcb/list.xhtml");
  }

  /** FacesContext あり + https+443: 標準ポート省略で host のみ. */
  @Test
  void buildsAbsoluteUrlFromFacesContextWithHttpsStandardPort() {
    final AppUrlBuilder builder = new AppUrlBuilder();
    setExternalBaseUrl(builder, null);
    final String url =
        invokeWithFakeRequest(builder, "/list.xhtml", "https", "app.example.com", 443, "/rcb");
    assertThat(url).isEqualTo("https://app.example.com/rcb/list.xhtml");
  }
}
