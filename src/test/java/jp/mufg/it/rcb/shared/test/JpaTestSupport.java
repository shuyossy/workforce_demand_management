package jp.mufg.it.rcb.shared.test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

// 抑制理由:
//  - PMD.AvoidCatchingGenericException: トランザクション境界では原因種別に依らず
//    rollback が必要。runInTx の責務上 RuntimeException を一律に捕捉する。
//  - PMD.AvoidAccessibilityAlteration: 本クラスは「@PersistenceContext フィールドへ
//    リフレクションで EM を注入する」ことが責務であり setAccessible(true) は本質。
//    本番コードでは ban、テストインフラとして本クラス限定で許容する。
/**
 * JPA 結合テスト用の汎用支援ユーティリティ.
 *
 * <p>H2 + Hibernate standalone の bootstrap、{@link PersistenceContext} 注釈付き フィールドへの {@link
 * EntityManager} リフレクション注入、トランザクション境界での 実行ヘルパを提供する。新規 Repository Adapter が増えても本クラスを修正せず {@link
 * #injectEntityManager(Object, EntityManager)} で本物の Adapter インスタンスに EM を差し込めるため、Adapter ごとの専用
 * Support クラスを増やす必要がない。
 */
@SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.AvoidAccessibilityAlteration"})
public final class JpaTestSupport {

  private JpaTestSupport() {
    // ユーティリティクラスのためインスタンス化禁止.
  }

  /**
   * H2 にスキーマを作成し EntityManagerFactory を生成する.
   *
   * <p>DDL ファイルは classpath ルートからの絶対パスで指定する（例: {@code "/db/migration/V1__create_xxx.sql"}）。指定順に
   * DriverManager 経由で 一括 execute する。テスト IT クラスごとに異なる mem DB 名を指定すれば、同一 JVM フォーク内で 他クラスの DDL /
   * データと衝突せず独立した DB を持てる（{@code jakarta.persistence.jdbc.url} を persistence.xml の
   * 既定値に対して明示的に上書きするため）。
   *
   * @param persistenceUnitName persistence.xml に定義された PU 名
   * @param jdbcUrl H2 接続 URL（例: {@code jdbc:h2:mem:xxx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}）
   * @param ddlResourcePaths classpath 上の DDL リソースパス（1 つ以上）
   * @return 生成された EntityManagerFactory
   * @throws IOException DDL ファイル読み込み失敗
   * @throws SQLException DDL 実行失敗
   */
  public static EntityManagerFactory bootstrapH2(
      final String persistenceUnitName, final String jdbcUrl, final String... ddlResourcePaths)
      throws IOException, SQLException {
    try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
        Statement statement = conn.createStatement()) {
      for (final String resourcePath : ddlResourcePaths) {
        statement.execute(readResource(resourcePath));
      }
    }
    return Persistence.createEntityManagerFactory(
        persistenceUnitName, Map.of("jakarta.persistence.jdbc.url", jdbcUrl));
  }

  /**
   * 任意の Adapter インスタンスの {@link PersistenceContext} 注釈付きフィールドに {@link EntityManager} を注入する.
   *
   * <p>本番では WildFly が container-managed で注入する経路を、テストではリフレクション 経由で差し替える。フィールドの可視性 (private /
   * package-private) に依存しない。 新規 Repository Adapter が増えても本メソッドは無修正で流用可能。
   *
   * @param adapter @PersistenceContext フィールドを持つ Adapter インスタンス
   * @param em 注入する EntityManager
   * @param <T> Adapter 型
   * @return 引数の adapter（メソッドチェーン用）
   */
  public static <T> T injectEntityManager(final T adapter, final EntityManager em) {
    for (final Field field : adapter.getClass().getDeclaredFields()) {
      if (field.isAnnotationPresent(PersistenceContext.class)) {
        try {
          field.setAccessible(true);
          field.set(adapter, em);
          return adapter;
        } catch (IllegalAccessException e) {
          throw new IllegalStateException(
              "Failed to inject EntityManager into " + adapter.getClass().getName(), e);
        }
      }
    }
    throw new IllegalStateException(
        "No @PersistenceContext field found on " + adapter.getClass().getName());
  }

  /**
   * 与えられた {@link Runnable} をトランザクション境界で実行する.
   *
   * <p>action 内で例外が発生した場合は rollback してから再 throw する。
   *
   * @param em 対象 EntityManager
   * @param action 実行するロジック
   */
  public static void runInTx(final EntityManager em, final Runnable action) {
    final EntityTransaction tx = em.getTransaction();
    tx.begin();
    try {
      action.run();
      tx.commit();
    } catch (RuntimeException e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      throw e;
    }
  }

  /**
   * classpath 上の DDL ファイル全体を文字列として読み込む.
   *
   * @param resourcePath classpath ルートからの絶対パス
   * @return ファイル内容
   * @throws IOException 読み込み失敗
   */
  private static String readResource(final String resourcePath) throws IOException {
    try (InputStream input = JpaTestSupport.class.getResourceAsStream(resourcePath)) {
      if (input == null) {
        throw new IOException("Resource not found on classpath: " + resourcePath);
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        final StringBuilder sql = new StringBuilder();
        String line = reader.readLine();
        while (line != null) {
          sql.append(line).append('\n');
          line = reader.readLine();
        }
        return sql.toString();
      }
    }
  }
}
