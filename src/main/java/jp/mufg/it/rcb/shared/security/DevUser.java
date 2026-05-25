package jp.mufg.it.rcb.shared.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 抑制理由:
//  - checkstyle:abbreviationaswordinname:
//    社内ライブラリ IF (UserPositionDto.layerSCode) に合わせた業務命名。
/**
 * 開発ログイン画面のプルダウン表示用ユーザ.
 *
 * <p>{@code dev-users.yml} から読み込まれ、表示ラベルは 「社員番号 - 氏名（所属部門名 / 職層 S コード）」形式。
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public class DevUser {

  /** 社員番号. */
  private final String empNum;

  /** 氏名. */
  private final String name;

  /** 主務所属部門部課ユニット ID. */
  private final String orgId;

  /** 主務所属部門名. */
  private final String orgName;

  /** 主務の職層 S コード. */
  private final String layerSCode;

  /**
   * プルダウン表示用のラベル文字列を組み立てる.
   *
   * @return "E0001 - 山田 太郎（リテール開発部 第一課 / L1）" 形式のラベル
   */
  public String getDisplayLabel() {
    return empNum + " - " + name + "（" + orgName + " / " + layerSCode + "）";
  }
}
