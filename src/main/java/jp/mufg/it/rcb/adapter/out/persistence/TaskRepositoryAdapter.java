package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;

/** {@link TaskRepositoryPort} の JPA 実装（appPU をインジェクション）. */
@ApplicationScoped
public class TaskRepositoryAdapter implements TaskRepositoryPort {

  /** JPA の永続化コンテキスト（appPU）. テストから差し替え可能とするため package-private とする. */
  /* default */ @PersistenceContext(unitName = "appPU")
  EntityManager em;

  /** ドメインと Entity を相互変換するマッパー. */
  private final TaskMapper mapper;

  /**
   * CDI コンストラクタインジェクション.
   *
   * @param mapper ドメインと Entity を相互変換するマッパー
   */
  @Inject
  public TaskRepositoryAdapter(final TaskMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Task save(final Task task) {
    if (task.getId() == null) {
      final TaskEntity entity = mapper.toEntity(task);
      em.persist(entity);
      return mapper.toDomain(entity);
    }
    final TaskEntity existing = em.find(TaskEntity.class, task.getId());
    if (existing == null) {
      throw new IllegalStateException("Entity not found for id=" + task.getId());
    }
    mapper.applyToEntity(task, existing);
    return mapper.toDomain(existing);
  }

  @Override
  public Optional<Task> findById(final long taskId) {
    return Optional.ofNullable(em.find(TaskEntity.class, taskId)).map(mapper::toDomain);
  }

  @Override
  public List<Task> findAllOrderByCreatedAtDesc() {
    final TypedQuery<TaskEntity> query =
        em.createQuery("SELECT e FROM TaskEntity e ORDER BY e.createdAt DESC", TaskEntity.class);
    return query.getResultList().stream().map(mapper::toDomain).toList();
  }
}
