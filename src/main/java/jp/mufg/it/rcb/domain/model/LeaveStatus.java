package jp.mufg.it.rcb.domain.model;

/** 休暇申請の状態. */
public enum LeaveStatus {
  PENDING,
  APPROVED,
  REJECTED;

  /** 状態遷移可能か判定する. 承認/却下は申請中（PENDING）からのみ可能。 */
  public boolean canTransitionTo(final LeaveStatus next) {
    return this == PENDING && (next == APPROVED || next == REJECTED);
  }
}
