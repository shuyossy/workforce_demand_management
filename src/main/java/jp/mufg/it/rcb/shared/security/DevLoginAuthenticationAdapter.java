package jp.mufg.it.rcb.shared.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.mufg.it.rcb.userinfo.dto.UserDto;
import jp.mufg.it.rcb.userinfo.dto.UserPositionDto;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

// 抑制理由:
//  - checkstyle:abbreviationaswordinname:
//    社内ライブラリ IF (UserPositionDto.layerSCode) に合わせた業務命名。
/**
 * 開発用 {@link AuthenticationPort} 実装. {@code dev-users.yml} を読み込んで {@code empNum} を {@link UserDto}
 * に解決する.
 *
 * <p>本番環境向けの HeaderAuthenticationAdapter（認証ヘッダ解釈）と差し替え可能なよう、 共通 IF ({@link AuthenticationPort})
 * を介して提供する。
 */
@ApplicationScoped
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public class DevLoginAuthenticationAdapter implements AuthenticationPort {

  /** {@code dev-users.yml} のクラスパス上のパス. */
  @Inject
  @ConfigProperty(name = "app.dev-users.path")
  /* default */ String devUsersPath;

  /** 読み込み済みの開発ユーザ一覧（@PostConstruct で初期化）. */
  private final List<DevUser> devUsers = new ArrayList<>();

  /** デフォルトコンストラクタ（CDI 仕様により public 引数なしが必要）. */
  public DevLoginAuthenticationAdapter() {
    // CDI Bean のため初期化処理は @PostConstruct で実施する.
  }

  /**
   * {@code dev-users.yml} を SnakeYAML でロードして {@link #devUsers} を構築する.
   *
   * <p>クラスパスに YAML が無い／構造が想定外な場合は {@link IllegalStateException} を投げて アプリ起動を停止させる。
   */
  @PostConstruct
  /* default */ void init() {
    try (InputStream stream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(devUsersPath)) {
      if (stream == null) {
        throw new IllegalStateException("dev-users.yml not found at classpath: " + devUsersPath);
      }
      loadFrom(stream);
    } catch (IOException | YAMLException e) {
      throw new IllegalStateException("Failed to load dev-users.yml", e);
    }
  }

  /**
   * 入力ストリームから YAML をパースし {@link #devUsers} を満たす.
   *
   * @param stream YAML の入力ストリーム
   */
  private void loadFrom(final InputStream stream) {
    final Yaml yaml = new Yaml();
    final Map<String, Object> root = yaml.load(stream);
    final Object usersObj = root == null ? null : root.get("dev-users");
    if (!(usersObj instanceof List<?> list)) {
      throw new IllegalStateException("dev-users.yml: 'dev-users' must be a list");
    }
    for (final Object item : list) {
      if (item instanceof Map<?, ?> entry) {
        devUsers.add(toDevUser(entry));
      }
    }
  }

  @Override
  public List<DevUser> listAvailableUsers() {
    return List.copyOf(devUsers);
  }

  @Override
  public Optional<UserDto> authenticate(final String empNum) {
    return devUsers.stream()
        .filter(u -> u.getEmpNum().equals(empNum))
        .findFirst()
        .map(this::toUserDto);
  }

  /**
   * YAML から読み込んだ 1 ユーザ分の Map を {@link DevUser} に変換する.
   *
   * @param entry YAML エントリ
   * @return 変換後の {@link DevUser}
   */
  private DevUser toDevUser(final Map<?, ?> entry) {
    return new DevUser(
        (String) entry.get("empNum"),
        (String) entry.get("name"),
        (String) entry.get("orgId"),
        (String) entry.get("orgName"),
        (String) entry.get("layerSCode"));
  }

  /**
   * {@link DevUser} を {@link UserDto} に変換する.
   *
   * <p>主務（mainSubNum=1）の {@link UserPositionDto} を 1 件だけ詰めた DTO を返す。
   *
   * @param devUser 開発ユーザ
   * @return 認証後の {@link UserDto}
   */
  private UserDto toUserDto(final DevUser devUser) {
    final UserDto dto = new UserDto();
    dto.setEmpNum(devUser.getEmpNum());
    dto.setName(devUser.getName());
    final UserPositionDto pos = new UserPositionDto();
    pos.setOrgId(devUser.getOrgId());
    pos.setGroupName(devUser.getOrgName());
    pos.setLayerSCode(devUser.getLayerSCode());
    pos.setMainSubNum(1);
    dto.getUserPositionDtoMap().put(1, pos);
    return dto;
  }
}
