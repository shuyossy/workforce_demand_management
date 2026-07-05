package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jp.mufg.it.rcb.domain.model.Task;

/** ドメイン Task と JPA Entity TaskEntity を相互変換するマッパー. */
@ApplicationScoped
public class TaskMapper {

  /** デフォルトコンストラクタ（CDI 仕様により公開する）. */
  public TaskMapper() {
    // CDI が ApplicationScoped Bean として生成するため初期化処理は不要。
  }

  /**
   * ドメインから新規 Entity を生成する.
   *
   * @param domain 変換元ドメイン（null 不可）
   * @return 新規生成された {@link TaskEntity}
   */
  public TaskEntity toEntity(final Task domain) {
    final TaskEntity entity = new TaskEntity();
    applyToEntity(domain, entity);
    return entity;
  }

  /**
   * 既存 Entity にドメインの値を反映する（in-place 更新）.
   *
   * @param src 反映元ドメイン（null 不可）
   * @param dst 反映先 Entity（null 不可）
   */
  public void applyToEntity(final Task src, final TaskEntity dst) {
    dst.setId(src.getId());
    dst.setTitle(src.getTitle());
    dst.setStatus(src.getStatus());
    dst.setCreatedAt(src.getCreatedAt());
    dst.setCompletedAt(src.getCompletedAt());
  }

  /**
   * Entity をドメインへ復元する.
   *
   * @param entity 変換元 Entity（null 不可）
   * @return 復元された {@link Task}
   */
  public Task toDomain(final TaskEntity entity) {
    return Task.reconstruct(
        entity.getId(),
        entity.getTitle(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getCompletedAt());
  }
}
