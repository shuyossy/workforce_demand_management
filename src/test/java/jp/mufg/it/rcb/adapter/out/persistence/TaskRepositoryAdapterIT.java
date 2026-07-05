package jp.mufg.it.rcb.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import jp.mufg.it.rcb.shared.test.JpaTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

// 抑制理由:
//  - checkstyle:AbbreviationAsWordInName: クラス名末尾の "IT" は maven-failsafe が結合テストを
//    識別する標準命名規約であり、Google Java Style の連続大文字規則と構造的に衝突するため本クラス限定で抑制する。
/** {@link TaskRepositoryAdapter} の結合テスト（H2 + 本物 Mapper）. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class TaskRepositoryAdapterIT {

  /** H2 bootstrap で生成する EntityManagerFactory. */
  private EntityManagerFactory emf;

  /** 各テストごとの EntityManager. */
  private EntityManager em;

  /** テスト対象（本物の Adapter）. */
  private TaskRepositoryAdapter adapter;

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ TaskRepositoryAdapterIT() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** H2 にスキーマを作成する. */
  @BeforeAll
  void setUpAll() throws IOException, SQLException {
    emf =
        JpaTestSupport.bootstrapH2(
            "appTestPU",
            "jdbc:h2:mem:app-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "/db/migration/V1__init.sql");
  }

  /** EntityManagerFactory を閉じる. */
  @AfterAll
  void tearDownAll() {
    if (emf != null) {
      emf.close();
    }
  }

  /** 各テスト前に空テーブルの Adapter を組み立てる. */
  @BeforeEach
  void setUp() {
    em = emf.createEntityManager();
    JpaTestSupport.runInTx(em, () -> em.createQuery("DELETE FROM TaskEntity").executeUpdate());
    adapter = JpaTestSupport.injectEntityManager(new TaskRepositoryAdapter(new TaskMapper()), em);
  }

  /** EntityManager を閉じる. */
  @AfterEach
  void tearDown() {
    if (em != null && em.isOpen()) {
      em.close();
    }
  }

  /** save() で採番され、findById() で取得できる. */
  @Test
  void savesAndFindsById() {
    final Task[] saved = new Task[1];
    JpaTestSupport.runInTx(
        em,
        () -> saved[0] = adapter.save(Task.create("買い物", Instant.parse("2026-07-03T09:00:00Z"))));
    assertThat(saved[0].getId()).isNotNull();

    em.clear();
    final Optional<Task> found = adapter.findById(saved[0].getId());
    assertThat(found).isPresent();
    assertThat(found.get().getTitle()).isEqualTo("買い物");
    assertThat(found.get().getStatus()).isEqualTo(TaskStatus.TODO);
  }

  /** findById() は存在しない ID で空を返す. */
  @Test
  void findByIdReturnsEmptyWhenMissing() {
    assertThat(adapter.findById(999L)).isEmpty();
  }

  /** findAllOrderByCreatedAtDesc() は作成日時の降順で返す. */
  @Test
  void findsAllOrderedByCreatedAtDesc() {
    JpaTestSupport.runInTx(
        em,
        () -> {
          adapter.save(Task.create("古い", Instant.parse("2026-07-01T09:00:00Z")));
          adapter.save(Task.create("新しい", Instant.parse("2026-07-03T09:00:00Z")));
        });
    em.clear();

    final List<Task> all = adapter.findAllOrderByCreatedAtDesc();
    assertThat(all).extracting(Task::getTitle).containsExactly("新しい", "古い");
  }

  /** save() の更新経路：既存タスクを完了状態で保存し直せる. */
  @Test
  void updatesExistingTask() {
    final Task[] saved = new Task[1];
    JpaTestSupport.runInTx(
        em,
        () -> saved[0] = adapter.save(Task.create("会議", Instant.parse("2026-07-03T09:00:00Z"))));
    em.clear();

    final Task loaded = adapter.findById(saved[0].getId()).orElseThrow();
    loaded.complete(Instant.parse("2026-07-03T10:00:00Z"));
    JpaTestSupport.runInTx(em, () -> adapter.save(loaded));
    em.clear();

    final Task reloaded = adapter.findById(saved[0].getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(reloaded.getCompletedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
  }

  /** save() の更新経路：存在しない ID を持つ Task を渡すと防御的に例外を投げる（データ不整合の想定外系）. */
  @Test
  void saveThrowsWhenUpdatingNonExistentEntity() {
    final Task ghost =
        Task.reconstruct(
            999_999L, "存在しない", TaskStatus.TODO, Instant.parse("2026-07-03T09:00:00Z"), null);

    assertThatThrownBy(() -> JpaTestSupport.runInTx(em, () -> adapter.save(ghost)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("999999");
  }
}
