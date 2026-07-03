package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * タスク新規作成ユースケースの入力コマンド.
 *
 * <p>Bean Validation でタイトルの必須・最大長（DB の title VARCHAR(100) と一致）を検証する。
 */
@Getter
@Builder
@AllArgsConstructor
public class CreateTaskCommand {

  /** タイトル（必須、100 字以内）. */
  @NotBlank
  @Size(max = 100)
  private final String title;
}
