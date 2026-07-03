package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import jp.mufg.it.rcb.exception.inner.MSTBusinessException;
import jp.mufg.it.rcb.exception.inner.MSTBusinessNonRecoverException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link CompleteTaskService} の単体テスト. */
@ExtendWith(MockitoExtension.class)
class CompleteTaskServiceTest {

  /** 永続化 port のモック. */
  @Mock private TaskRepositoryPort repository;

  /** Logger のモック. */
  @Mock private Logger sysLogger;

  /** 固定時刻クロック（完了日時の検証用）. */
  private final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-07-03T10:00:00Z"));

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ CompleteTaskServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** TODO のタスク（id=1）を生成する. */
  private Task todoTask() {
    return Task.reconstruct(
        1L, "買い物", TaskStatus.TODO, Instant.parse("2026-07-03T09:00:00Z"), null);
  }

  /** sysLogger を差し込んだ Service を構築する. */
  private CompleteTaskService newService() {
    final CompleteTaskService svc = new CompleteTaskService(repository, clock);
    svc.sysLogger = sysLogger;
    return svc;
  }

  /** 正常系：TODO を DONE に遷移し完了日時を設定して保存する. */
  @Test
  void completesTodoTask() {
    when(repository.findById(1L)).thenReturn(Optional.of(todoTask()));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    newService().complete(1L);

    final ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
    verify(repository).save(captor.capture());
    final Task saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(saved.getCompletedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
  }

  /** 異常系（回復可）：既に DONE のタスクは MSTBusinessException で拒否する. */
  @Test
  void rejectsAlreadyDoneWithRecoverableError() {
    final Task done = todoTask();
    done.complete(Instant.parse("2026-07-03T09:30:00Z"));
    when(repository.findById(1L)).thenReturn(Optional.of(done));

    final CompleteTaskService svc = newService();
    assertThatThrownBy(() -> svc.complete(1L)).isInstanceOf(MSTBusinessException.class);
    verify(repository, never()).save(any());
  }

  /** 異常系（回復不可）：対象が存在しない場合は MSTBusinessNonRecoverException を投げる. */
  @Test
  void throwsNonRecoverableWhenNotFound() {
    when(repository.findById(999L)).thenReturn(Optional.empty());

    final CompleteTaskService svc = newService();
    assertThatThrownBy(() -> svc.complete(999L)).isInstanceOf(MSTBusinessNonRecoverException.class);
    verify(repository, never()).save(any());
  }
}
