package jp.mufg.it.rcb.shared.security;

import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.userinfo.dto.UserDto;

/**
 * 認証処理を担う共有 port.
 *
 * <p>開発環境では {@link DevLoginAuthenticationAdapter} が実装し、本番では将来追加される予定の
 * HeaderAuthenticationAdapter（リクエストヘッダ経由の認証情報を解釈する実装）に差し替える。
 */
public interface AuthenticationPort {

  /**
   * ログイン候補ユーザ一覧を返す（開発用：プルダウン表示用）.
   *
   * @return 開発用ユーザの一覧（不変リスト）
   */
  List<DevUser> listAvailableUsers();

  /**
   * 指定 empNum で認証して {@link UserDto} を返す（開発用：パスワード不要）.
   *
   * @param empNum 社員番号
   * @return 該当ユーザが存在すれば {@link UserDto}、なければ {@link Optional#empty()}
   */
  Optional<UserDto> authenticate(String empNum);
}
