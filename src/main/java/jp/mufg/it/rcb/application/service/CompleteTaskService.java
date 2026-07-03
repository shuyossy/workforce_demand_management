package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.CompleteTaskUseCase;
import jp.mufg.it.rcb.application.port.out.ClockPort;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.exception.inner.MSTBusinessNonRecoverException;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;

/**
 * タスク完了ユースケースの実装.
 *
 * <p>対象不存在は「削除機能を持たない本サンプルでは通常起こり得ない想定外状態（POST 偽装/データ不整合）」として 回復不可の {@link
 * MSTBusinessNonRecoverException} を送出する。既に完了済みの再完了はユーザが取り消せる 回復可の業務エラー {@link MSTBusinessException}
 * として現画面に留置する。
 */
@ApplicationScoped
public class CompleteTaskService implements CompleteTaskUseCase {

  /** 永続化 port. */
  private final TaskRepositoryPort repository;

  /** 時刻取得 port. */
  private final ClockPort clock;

  /**
   * 業務イベント用 Logger.
   *
   * <p>パッケージ private 可視性は同パッケージのテストから直接 mock を差し込めるよう意図的に採用している。
   */
  @Inject
  @InjectLogger(LoggerType.SYSTEM)
  /* default */ Logger sysLogger;

  /**
   * 依存を注入する.
   *
   * @param repository 永続化 port
   * @param clock 時刻取得 port
   */
  @Inject
  public CompleteTaskService(final TaskRepositoryPort repository, final ClockPort clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void complete(final long taskId) {
    final Task task =
        repository
            .findById(taskId)
            .orElseThrow(
                () ->
                    new MSTBusinessNonRecoverException(
                        new String[] {"task not found: id=" + taskId},
                        new String[] {"指定されたタスクが見つかりません"},
                        null));

    if (task.getStatus() != TaskStatus.TODO) {
      throw new MSTBusinessException("既に完了したタスクです");
    }

    task.complete(clock.now());
    repository.save(task);
    // PMD GuardLogStatement: Object[] パラメータ配列生成を回避するため Level ガードを挟む.
    if (sysLogger.isLoggable(Level.INFO)) {
      sysLogger.log(Level.INFO, "RCB00002-I", new Object[] {task.getId()});
    }
  }
}
