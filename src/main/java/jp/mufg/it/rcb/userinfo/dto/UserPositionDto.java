package jp.mufg.it.rcb.userinfo.dto;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/** ログインユーザーの所属情報を格納したDTO(Data Transfer Object)クラスです。 */
@Getter
@Setter
public class UserPositionDto implements Serializable {
  private static final long serialVersionUID = -1134155575055721608L;

  // 所属情報ID
  private String blngId;

  // 所属部門部課ユニットID
  private String orgId;

  // 所属部門部課ユニットコード
  private String orgCode;

  // 所属部門コード
  private String groupCode;

  // 所属部門名
  private String groupName;

  // 所属部門部ID
  private String deptId;

  // 所属部門部コード
  private String deptCode;

  // 所属部名
  private String deptName;

  // 所属課ユニットID
  private String unitId;

  // 所属課ユニット名
  private String unitName;

  // 職層Sコード
  private String layerSCode;

  // 職層S名
  private String layerSName;

  // 職務コード
  private String pstnCode;

  // 職務名
  private String pstnName;

  // 主務兼務番号
  private Integer mainSubNum;
}
