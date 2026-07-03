package jp.mufg.it.rcb.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.logging.Logger;
import jp.mufg.it.rcb.application.port.in.CreateTaskCommand;
import jp.mufg.it.rcb.application.port.out.TaskRepositoryPort;
import jp.mufg.it.rcb.application.service.support.FixedClockStub;
import jp.mufg.it.rcb.domain.model.Task;
import jp.mufg.it.rcb.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link CreateTaskService} の単体テスト. */
@ExtendWith(MockitoExtension.class)
class CreateTaskServiceTest {

  /** 永続化 port のモック. */
  @Mock private TaskRepositoryPort repository;

  /** Logger のモック（@InjectLogger は単体テストでは注入されないため）. */
  @Mock private Logger sysLogger;

  /** 固定時刻クロック. */
  private final FixedClockStub clock = new FixedClockStub(Instant.parse("2026-07-03T09:00:00Z"));

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ CreateTaskServiceTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /** sysLogger を差し込んだ Service を構築する. */
  private CreateTaskService newService() {
    final CreateTaskService svc = new CreateTaskService(repository, clock);
    svc.sysLogger = sysLogger;
    return svc;
  }

  /** create() は TODO のタスクを作成日時付きで保存する. */
  @Test
  void createsTodoTaskWithClockTime() {
    when(repository.save(any()))
        .thenAnswer(
            inv ->
                Task.reconstruct(
                    1L, "買い物", TaskStatus.TODO, Instant.parse("2026-07-03T09:00:00Z"), null));

    newService().create(CreateTaskCommand.builder().title("買い物").build());

    final ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
    verify(repository).save(captor.capture());
    final Task saved = captor.getValue();
    assertThat(saved.getTitle()).isEqualTo("買い物");
    assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
    assertThat(saved.getId()).isNull();
  }
}
