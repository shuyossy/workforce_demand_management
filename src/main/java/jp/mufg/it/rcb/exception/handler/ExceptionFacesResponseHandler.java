package jp.mufg.it.rcb.exception.handler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEventContext;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import jp.mufg.it.rcb.exception.ErrorCode;
import jp.mufg.it.rcb.exception.ExceptionHelper;
import jp.mufg.it.rcb.exception.inner.InnerRuntimeException;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @brief JSFでの例外発生時にメッセージ格納、画面遷移を行うクラス。
 * @details 例外が発生した場合、適切なエラーメッセージを格納し、適切なエラーページに遷移
 */
@ApplicationScoped
public class ExceptionFacesResponseHandler {

  private ExceptionHelper helper;

  /**
   * @brief 引数なしコンストラクタ。
   */
  public ExceptionFacesResponseHandler() {}

  /**
   * @brief コンストラクタ。
   */
  @Inject
  public ExceptionFacesResponseHandler(ExceptionHelper helper) {
    this.helper = helper;
  }

  /**
   * @brief 発生した例外に応じてメッセージ格納、画面遷移を行う。
   */
  public void handleErrorResponse(Throwable throwable, ExceptionQueuedEventContext eventContext)
      throws IOException {

    // FacesContextの取得
    FacesContext facesContext = eventContext.getContext();
    // 内部例外の取得
    InnerRuntimeException innerException = helper.getInnerException(throwable);
    // 内部例外が存在する場合
    if (innerException != null) {
      handleKnownException(innerException, facesContext);
      return;
    }
    // 想定外のエラー
    handleUnknownException(facesContext);
  }

  /**
   * @brief 内部例外のHTTPステータスに応じて遷移先、表示メッセージを判定
   *     MSTBusinessExceptionのみredirectPageが設定されていた場合はhttpステータスに関わらず指定された画面に遷移
   */
  private void handleKnownException(InnerRuntimeException innerException, FacesContext facesContext)
      throws IOException {

    String errorCode = innerException.getErrorCode();
    String responseMessage = helper.getErrorMessage(innerException);
    int httpStatus = helper.getHttpStatusCode(innerException);

    // MSTBusinessExceptionの場合、リダイレクトページが設定されているか確認
    // MSTBusinessExceptionのみredirectPageが設定されていた場合はhttpステータスに関わらず指定された画面に遷移
    if (innerException instanceof MSTBusinessException) {
      MSTBusinessException mstException = (MSTBusinessException) innerException;
      String redirectPage = mstException.getRedirectPage();
      if (redirectPage != null && !redirectPage.isEmpty()) {
        redirectWithErrorMessage(facesContext, "エラー", responseMessage, redirectPage);
        return;
      }
    }

    // ステータス：400
    if (httpStatus == 400) {
      // リダイレクトはせず元画面のまま
      facesContext.addMessage(
          null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "エラー", responseMessage));
      return;
    }

    // ステータス：400系(400以外)、500系
    // httpステータスで指定された画面にリダイレクト
    if (httpStatus / 100 == 4 || httpStatus / 100 == 5) {
      String redirectUrl = getRedirectUrlForStatus(facesContext, httpStatus);
      redirectWithErrorMessage(facesContext, "エラー", responseMessage, redirectUrl);
      return;
    }

    // 上記以外のステータス（想定外のステータス）
    String redirectUrl = getRedirectUrlForStatus(facesContext, httpStatus);
    redirectWithErrorMessage(
        facesContext, "本来このハンドラーで制御しないHttpステータスエラー", responseMessage, redirectUrl);
  }

  /**
   * @brief 内部例外が存在しない場合の処理を行う。
   * @throws IOException ExternalContextでRedirect時にinput/output error発生した場合
   */
  private void handleUnknownException(FacesContext facesContext) throws IOException {

    String errorCode = ErrorCode.UNKNOWN.getErrorCode();
    String responseMessage = helper.getErrorMessage(errorCode);
    int httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

    // 遷移先を取得
    String redirectUrl = getRedirectUrlForStatus(facesContext, httpStatus);
    // システムエラーページへ遷移
    redirectWithErrorMessage(facesContext, "システムエラー", responseMessage, redirectUrl);
  }

  /**
   * @brief HTTPレスポンスをごとに、各アプリのfaces-config.xmlから遷移先を取得
   * @throws IOException ExternalContextでRedirect時にinput/output error発生した場合
   */
  private String getRedirectUrlForStatus(FacesContext facesContext, int httpStatus)
      throws IOException {

    ExternalContext externalContext = facesContext.getExternalContext();
    String redirectUrl = null;

    // faces-config.xmlのコンテキストパラメータからリダイレクト先のURLを取得
    ServletContext servletContext = (ServletContext) externalContext.getContext();
    InputStream inputStream = servletContext.getResourceAsStream("/WEB-INF/faces-config.xml");

    if (inputStream != null) {
      try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);
        document.getDocumentElement().normalize();

        String propertyKey = "error.page." + httpStatus;
        NodeList entryNodes = document.getElementsByTagName("entry");

        for (int i = 0; i < entryNodes.getLength(); i++) {
          Element entryElement = (Element) entryNodes.item(i);
          String key = entryElement.getElementsByTagName("key").item(0).getTextContent();
          if (propertyKey.equals(key)) {
            redirectUrl = entryElement.getElementsByTagName("value").item(0).getTextContent();
            break;
          }
        }

        // コンテキストパラメータが設定されていない場合はデフォルトのURLを使用
        if (redirectUrl == null || redirectUrl.isEmpty()) {
          for (int i = 0; i < entryNodes.getLength(); i++) {
            Element entryElement = (Element) entryNodes.item(i);
            String key = entryElement.getElementsByTagName("key").item(0).getTextContent();
            if ("error.page.default".equals(key)) {
              redirectUrl = entryElement.getElementsByTagName("value").item(0).getTextContent();
              break;
            }
          }
        }
      } catch (Exception e) {
        throw new IOException("Failed to parse faces-config.xml", e);
      } finally {
        inputStream.close();
      }
    }
    return redirectUrl;
  }

  /**
   * @brief エラーメッセージを設定し、指定されたページにリダイレクトする。
   * @throws IOException ExternalContextでRedirect時にinput/output error発生した場合
   */
  private void redirectWithErrorMessage(
      FacesContext facesContext, String title, String responseMessage, String redirectPage)
      throws IOException {

    facesContext.addMessage(
        null, new FacesMessage(FacesMessage.SEVERITY_ERROR, title, responseMessage));
    // メッセージの永続化
    ExternalContext externalContext = facesContext.getExternalContext();
    externalContext.getFlash().setKeepMessages(true);

    String contextPath = externalContext.getRequestContextPath();
    externalContext.redirect(contextPath + redirectPage);
  }
}
