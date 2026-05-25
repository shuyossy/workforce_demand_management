package jp.mufg.it.rcb.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import jp.mufg.it.rcb.userinfo.dto.UserPositionDto;
import org.junit.jupiter.api.Test;

/**
 * {@link DevLoginAuthenticationAdapter} の単体テスト.
 *
 * <p>{@code @PostConstruct} の {@code init()} と {@code authenticate()} の挙動を、CDI コンテナ無しで
 * リフレクション経由で直接駆動する。 クラスパス上の {@code dev-users.yml} を実ファイルとして読ませる。
 */
class DevLoginAuthenticationAdapterTest {

  /** デフォルトコンストラクタ（PMD AtLeastOneConstructor 対応のため明示宣言）. */
  /* default */ DevLoginAuthenticationAdapterTest() {
    // テストクラスは状態を持たないため初期化処理は不要。
  }

  /**
   * リフレクションで package-private フィールド {@code devUsersPath} と {@code init()} を駆動する.
   *
   * @return 初期化済み {@link DevLoginAuthenticationAdapter}
   */
  private static DevLoginAuthenticationAdapter newInitialized() {
    try {
      final DevLoginAuthenticationAdapter adapter = new DevLoginAuthenticationAdapter();
      final Field pathField = DevLoginAuthenticationAdapter.class.getDeclaredField("devUsersPath");
      pathField.setAccessible(true);
      pathField.set(adapter, "dev-users.yml");
      final Method init = DevLoginAuthenticationAdapter.class.getDeclaredMethod("init");
      init.setAccessible(true);
      init.invoke(adapter);
      return adapter;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("リフレクション初期化に失敗", e);
    }
  }

  @Test
  void listAvailableUsersReturnsAllEntriesFromYaml() {
    final DevLoginAuthenticationAdapter adapter = newInitialized();
    final List<DevUser> users = adapter.listAvailableUsers();
    assertThat(users)
        .extracting(DevUser::getEmpNum)
        .containsExactlyInAnyOrder("E0001", "E0002", "E0003", "E0004");
  }

  /** 主務 UserPositionDto を含む UserDto への変換結果を 1 観測として 4 項目検証する. */
  @Test
  void authenticateReturnsUserDtoForKnownEmpNum() {
    final DevLoginAuthenticationAdapter adapter = newInitialized();
    final Optional<UserDto> result = adapter.authenticate("E0001");
    assertThat(result).isPresent();
    final UserDto dto = result.get();
    assertThat(dto.getEmpNum()).isEqualTo("E0001");
    assertThat(dto.getName()).isEqualTo("山田 太郎");
    final UserPositionDto position = dto.getMainUserPositionDto();
    assertThat(position.getLayerSCode()).isEqualTo("L1");
  }

  @Test
  void authenticateReturnsEmptyForUnknownEmpNum() {
    final DevLoginAuthenticationAdapter adapter = newInitialized();
    assertThat(adapter.authenticate("E9999")).isEmpty();
  }

  @Test
  void initThrowsWhenYamlPathIsMissing() {
    assertThatThrownBy(() -> invokeInitWithPath("no-such-file.yml"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dev-users.yml not found");
  }

  /** dev-users キーが List でない場合は IllegalStateException を投げる. */
  @Test
  void initRejectsDevUsersWhenValueIsNotList() {
    assertThatThrownBy(() -> invokeInitWithPath("dev-users-not-a-list.yml"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'dev-users' must be a list");
  }

  /** YAML 構文が壊れている場合は YAMLException を IllegalStateException に包んで投げる. */
  @Test
  void initThrowsWhenYamlIsMalformed() {
    assertThatThrownBy(() -> invokeInitWithPath("dev-users-broken-yaml.yml"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to load dev-users.yml");
  }

  /**
   * 指定パスで {@code init()} を駆動し、リフレクションラップを剥がして元例外を再 throw する.
   *
   * <p>{@code PreserveStackTrace} は、テスト対象である内側の {@link IllegalStateException} を そのまま投げ直すために、外側の
   * {@link ReflectiveOperationException} ラッパを破棄する 設計上の意図的選択として個別抑制する。
   *
   * @param path {@code devUsersPath} に設定するパス
   */
  @SuppressWarnings("PMD.PreserveStackTrace")
  private static void invokeInitWithPath(final String path) {
    try {
      final DevLoginAuthenticationAdapter adapter = new DevLoginAuthenticationAdapter();
      final Field pathField = DevLoginAuthenticationAdapter.class.getDeclaredField("devUsersPath");
      pathField.setAccessible(true);
      pathField.set(adapter, path);
      final Method init = DevLoginAuthenticationAdapter.class.getDeclaredMethod("init");
      init.setAccessible(true);
      init.invoke(adapter);
    } catch (ReflectiveOperationException reflectEx) {
      final Throwable cause = reflectEx.getCause();
      if (cause instanceof IllegalStateException) {
        // init() が投げた IllegalStateException をそのまま再 throw する（テスト対象例外）.
        // InvocationTargetException ラップは捨て、cause のスタックトレースをそのまま提供する.
        throw (IllegalStateException) cause;
      }
      throw new IllegalStateException("リフレクション初期化に失敗", reflectEx);
    }
  }
}
