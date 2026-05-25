package jp.mufg.it.rcb.application.port.in;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 休暇申請一覧ユースケースの入力コマンド. */
@Getter
@Builder
@AllArgsConstructor
public class ListLeavesCommand {

  /** 一覧の取得範囲. */
  public enum Scope {
    /** 自分が申請したもの. */
    MINE,
    /** 自分が承認可能なもの. */
    APPROVABLE
  }

  /** 取得範囲（null 不可）. */
  @NotNull private final Scope scope;
}
