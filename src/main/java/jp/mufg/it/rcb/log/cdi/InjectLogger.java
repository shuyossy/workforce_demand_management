package jp.mufg.it.rcb.log.cdi;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Loggerの指定を行うアノテーション. */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
@Documented
public @interface InjectLogger {
  /**
   * Loggerの種類を設定する.
   *
   * @return Loggerの種類
   */
  @Nonbinding
  LoggerType value() default LoggerType.SYSTEM;
}
