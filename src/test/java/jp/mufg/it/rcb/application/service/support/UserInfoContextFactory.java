package jp.mufg.it.rcb.application.service.support;

import jp.mufg.it.rcb.userinfo.context.UserInfoContext;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import jp.mufg.it.rcb.userinfo.dto.UserPositionDto;

// 抑制理由:
//  - checkstyle:abbreviationaswordinname: 社内ライブラリ setLayerSCode と同一 IF を
//    維持するため連続大文字（LSC）の引数名を許容する。
//  - PMD.UseObjectForClearerAPI: テスト専用のフィクスチャ生成 API であり、5 個の
//    String 引数は UserDto / UserPositionDto の同一 IF に対応する。本番コードでは
//    なく、引数集約のために container 型を導入してもむしろテスト可読性が低下する。
/** テスト用に固定ユーザ情報を保持する {@link UserInfoContext} を生成するファクトリ. */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public final class UserInfoContextFactory {

  private UserInfoContextFactory() {
    // ユーティリティクラスのためインスタンス化禁止.
  }

  /**
   * 主務所属を 1 件持つ {@link UserInfoContext} を生成する（mainSubNum=1）.
   *
   * @param empNum 社員番号
   * @param name 氏名
   * @param orgId 組織 ID
   * @param groupName 組織名
   * @param layerSCode 職層 S コード
   * @return 生成済み UserInfoContext
   */
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  public static UserInfoContext create(
      final String empNum,
      final String name,
      final String orgId,
      final String groupName,
      final String layerSCode) {
    final UserDto user = new UserDto();
    user.setEmpNum(empNum);
    user.setName(name);
    final UserPositionDto position = new UserPositionDto();
    position.setOrgId(orgId);
    position.setGroupName(groupName);
    position.setLayerSCode(layerSCode);
    position.setMainSubNum(1);
    user.getUserPositionDtoMap().put(1, position);
    final UserInfoContext ctx = new UserInfoContext();
    ctx.setUser(user);
    return ctx;
  }
}
