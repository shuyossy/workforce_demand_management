package jp.mufg.it.rcb.shared.test;

import java.lang.reflect.Field;

// 抑制理由:
//  - PMD.AvoidAccessibilityAlteration: 本クラスは「package-private / private フィールドへ
//    リフレクションで値を注入する」ことが責務であり setAccessible(true) は本質。本番コードでは
//    ban、テストインフラとして本クラス限定で許容する。
//  - PMD.LinguisticNaming: injectField は「セッター意味論ではなくチェーン用 fluent API」
//    として target を返す。setX 命名を避けるため inject... を採用済み。
/**
 * 異なるパッケージから package-private / private フィールドへ値を注入するためのテスト用リフレクション支援.
 *
 * <p>結合テストでは本物の Service / Bean インスタンスを直接構築するが、ロガーなど CDI が container-managed
 * で注入するフィールドは異なるパッケージから直接代入できない。本クラスでフィールド名指定による注入を一元化する。
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
public final class ReflectionTestSupport {

  private ReflectionTestSupport() {
    // ユーティリティクラスのためインスタンス化禁止.
  }

  /**
   * 指定された名前のフィールドに値を注入する.
   *
   * <p>{@link Field#setAccessible(boolean)} を使うため可視性に依存しない。
   *
   * @param target 注入先インスタンス
   * @param fieldName フィールド名
   * @param value 注入する値
   * @param <T> ターゲット型
   * @return 引数 target（メソッドチェーン用）
   */
  public static <T> T injectField(final T target, final String fieldName, final Object value) {
    try {
      final Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
      return target;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException(
          "Failed to inject field '" + fieldName + "' on " + target.getClass().getName(), e);
    }
  }
}
