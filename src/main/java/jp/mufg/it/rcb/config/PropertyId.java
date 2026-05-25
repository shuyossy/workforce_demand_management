package jp.mufg.it.rcb.config;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 設定キーを表す CDI Qualifier アノテーション（社内ライブラリ準拠 IF として独自実装）.
 *
 * <p>社内ライブラリ準拠 IF コードからのみ参照される前提。
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.METHOD,
  ElementType.FIELD,
  ElementType.CONSTRUCTOR,
  ElementType.PARAMETER,
  ElementType.TYPE
})
@Documented
public @interface PropertyId {

  /**
   * 設定セット識別子.
   *
   * @return 識別子
   */
  @Nonbinding
  String value() default "default";
}
