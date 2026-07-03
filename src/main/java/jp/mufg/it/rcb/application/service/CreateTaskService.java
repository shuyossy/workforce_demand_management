package jp.mufg.it.rcb.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.logging.Level;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.CreateTaskCommand;
import jp.mufg.it.rcb.application.port.in.CreateTaskUseCase;
import jp.mufg.it.rcb.application.port.out.ClockPort;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.log.cdi.InjectLogger;
import jp.mufg.it.rcb.log.cdi.LoggerType;

/** タスク新規作成ユースケースの実装（成功時に業務イベント INFO ログを emit）. */
@ApplicationScoped
public class CreateTaskService implements CreateTaskUseCase {

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
  public CreateTaskService(final TaskRepositoryPort repository, final ClockPort clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void create(@Valid final CreateTaskCommand cmd) {
    final Task task = Task.create(cmd.getTitle(), clock.now());
    final Task saved = repository.save(task);
    // PMD GuardLogStatement: Object[] パラメータ配列生成を回避するため Level ガードを挟む.
    if (sysLogger.isLoggable(Level.INFO)) {
      sysLogger.log(Level.INFO, "RCB00001-I", new Object[] {saved.getId()});
    }
  }
}
