package jp.mufg.it.rcb.exception.handler;

import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;
import jakarta.inject.Inject;
import java.util.logging.Logger;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import lombok.NoArgsConstructor;

/**
 * @brief JSF用例外ハンドラーのファクトリクラス。
 * @details 例外が発生した際に、JSFフレームワークより呼び出され、以下のハンドラーを呼び出します。 1. ExceptionLogHandler:
 *     例外が発生した際にログ出力をコントロールするためのハンドラー 2. ExceptionFacesResponseHandler:
 *     例外が発生した際に、適切な画面遷移をコントロールするためのハンドラー 3. Logger:Javaの標準的なログ出力クラス。
 * @return なし
 * @see ExceptionHandlerFactory
 * @note このクラスは、例外ハンドラーの生成を行います。
 */
@NoArgsConstructor
public class FacesExceptionHandlerFactory extends ExceptionHandlerFactory {

  private ExceptionHandlerFactory parent;
  @Inject private ExceptionLogHandler logHandler; // 例外が発生した際にログを出力するためのハンドラー
  @Inject private ExceptionFacesResponseHandler responseHandler; // 例外が発生した際に、適切な画面遷移を行うためのハンドラー
  @Inject @InjectLogger private Logger logger; // Javaの標準的なログ出力クラス

  /**
   * @brief コンストラクタ
   * @details このコンストラクタは、親のExceptionHandlerFactoryを引数に取ります。
   * @param parent 親のExceptionHandlerFactory
   * @return なし
   * @see ExceptionHandlerFactory
   * @note このコンストラクタは、親のExceptionHandlerFactoryを引数に取ります。
   */
  @SuppressWarnings("deprecation")
  public FacesExceptionHandlerFactory(ExceptionHandlerFactory parent) {
    this.parent = parent;
  }

  /**
   * @brief 例外ハンドラーを取得します。
   * @details このメソッドは、新しいFacesExceptionHandlerを生成し、それを返します。
   * @return 新しいFacesExceptionHandler
   * @see FacesExceptionHandler
   * @note このメソッドは、新しいFacesExceptionHandlerを生成し、それを返します。
   */
  @Override
  public ExceptionHandler getExceptionHandler() {
    return new FacesExceptionHandler(
        parent.getExceptionHandler(), logHandler, responseHandler, logger);
  }
}
