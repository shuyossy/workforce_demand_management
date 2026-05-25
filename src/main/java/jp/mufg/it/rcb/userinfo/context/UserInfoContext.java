package jp.mufg.it.rcb.userinfo.context;

import jakarta.enterprise.context.SessionScoped;
import java.io.Serializable;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import lombok.Getter;
import lombok.Setter;

/**
 * ログイン済みユーザ情報を保持するセッションコンテキスト.
 *
 * <p>社内ライブラリ準拠 IF。社内ライブラリ置換時の互換性確保のため、フィールド・メソッド・スコープを <strong>変更してはいけない</strong>（他の社内ライブラリ準拠 IF
 * パッケージと同じ取り扱い）。
 */
@SessionScoped
@Getter
@Setter
public class UserInfoContext implements Serializable {

  private static final long serialVersionUID = 1L;

  /** ログイン済みユーザ（未ログイン時は null）. */
  private UserDto user;

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public UserInfoContext() {
    // CDI Bean のため初期化処理は不要.
  }
}
