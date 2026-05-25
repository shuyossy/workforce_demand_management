package jp.mufg.it.rcb.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.application.port.out.LeaveRepositoryPort;
import jp.mufg.it.rcb.domain.model.LeaveRequest;
import jp.mufg.it.rcb.domain.model.LeaveStatus;

/** {@link LeaveRepositoryPort} の JPA 実装（appPU をインジェクション）. */
@ApplicationScoped
public class LeaveRepositoryAdapter implements LeaveRepositoryPort {

  /** JPA の永続化コンテキスト（appPU）. テストから差し替え可能とするため package-private とする. */
  /* default */ @PersistenceContext(unitName = "appPU")
  EntityManager em;

  /** ドメインと Entity を相互変換するマッパー. */
  private final LeaveRequestMapper mapper;

  /**
   * CDI コンストラクタインジェクション.
   *
   * @param mapper ドメインと Entity を相互変換するマッパー
   */
  @Inject
  public LeaveRepositoryAdapter(final LeaveRequestMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public LeaveRequest save(final LeaveRequest request) {
    if (request.getId() == null) {
      final LeaveRequestEntity entity = mapper.toEntity(request);
      em.persist(entity);
      return mapper.toDomain(entity);
    }
    final LeaveRequestEntity existing = em.find(LeaveRequestEntity.class, request.getId());
    if (existing == null) {
      throw new IllegalStateException("Entity not found for id=" + request.getId());
    }
    mapper.applyToEntity(request, existing);
    return mapper.toDomain(existing);
  }

  @Override
  public Optional<LeaveRequest> findById(final long leaveRequestId) {
    return Optional.ofNullable(em.find(LeaveRequestEntity.class, leaveRequestId))
        .map(mapper::toDomain);
  }

  @Override
  public List<LeaveRequest> findByApplicantEmpNum(final String empNum) {
    final TypedQuery<LeaveRequestEntity> query =
        em.createQuery(
            "SELECT e FROM LeaveRequestEntity e WHERE e.applicantEmpNum = :empNum"
                + " ORDER BY e.appliedAt DESC",
            LeaveRequestEntity.class);
    query.setParameter("empNum", empNum);
    return query.getResultList().stream().map(mapper::toDomain).toList();
  }

  @Override
  public List<LeaveRequest> findApprovablePending(final String orgId, final String excludeEmpNum) {
    final TypedQuery<LeaveRequestEntity> query =
        em.createQuery(
            "SELECT e FROM LeaveRequestEntity e"
                + " WHERE e.applicantOrgId = :orgId"
                + "   AND e.status = :pending"
                + "   AND e.applicantEmpNum <> :excludeEmpNum"
                + " ORDER BY e.appliedAt ASC",
            LeaveRequestEntity.class);
    query.setParameter("orgId", orgId);
    query.setParameter("pending", LeaveStatus.PENDING);
    query.setParameter("excludeEmpNum", excludeEmpNum);
    return query.getResultList().stream().map(mapper::toDomain).toList();
  }
}
