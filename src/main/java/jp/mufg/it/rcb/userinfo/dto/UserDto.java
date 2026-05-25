package jp.mufg.it.rcb.userinfo.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** ログインユーザーの情報を格納したDTO */
@Getter
@Setter
public class UserDto implements Serializable {

  private static final long serialVersionUID = -1134155575055721608L;

  /* 個人ID */
  private String prsnId;

  /* ネットワークID */
  private String nwId;

  /* 社員番号 */
  private String empNum;

  /* 名前 */
  private String name;

  /* 名前（カナ） */
  private String nameKana;

  /* ユーザーの所属情報を格納したマップ（Keyは主務兼務番号） */
  private Map<Integer, UserPositionDto> userPositionDtoMap =
      new HashMap<Integer, UserPositionDto>();

  public UserPositionDto getMainUserPositionDto() {
    return userPositionDtoMap.get(1);
  }
}
