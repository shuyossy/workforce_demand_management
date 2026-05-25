# プロジェクト概要

このプロジェクトは Jakarta EE 系列の AI 主導開発に対応したモダンな開発環境のサンプルを提供するボイラープレート（`jakartaEE` ブランチ）である。`main` ブランチ（Spring Boot サンプル特化）から派生し、サンプルアプリの実体は次の PBI #4 で再構築される。

# 技術構成

## IDE

- VSCode

## バックエンド

- Jakarta EE（Maven、WildFly 32.0.1.Final 想定）
- ※ アプリケーションコードの実体は **PBI #4 で再構築** されるため、現状の `src/` は空骨格（`.gitkeep` のみ）

## フロントエンド

- xhtml（PBI #4 で構築予定。JS はバンドルせず各 xhtml ファイルに紐づける想定）

## CI/CD

- GitLab(セルフホスト版)

# 開発サイクル

シフトレフトを大前提とする（セキュリティも含めたソフトウェア品質の担保）。
様々なツールを利用して開発環境からリモートリポジトリにバグを持ち出さない、CI/CDパイプライン上でバグを含んだ資源をデプロイしないような仕組みを構築する。

AI 主導開発の方針上、Google Java Style 全規則と PMD 全カテゴリの両方を **コミット時点から hard gate** として強制する。pre-commit で差分ファイルに対し Spotless / Checkstyle blocking / Checkstyle google / PMD blocking / PMD full の 5 段を実行し、SpotBugs / JUnit / JaCoCo は差分単位化が困難なため pre-push と CI で全体実行する。詳細は `rules/README.md` を参照。

pre-push は 2 段化されている:

- **Phase 1**: `./mvnw -Pfast clean verify`（静的解析 + 単体 + 結合 + 本体 JaCoCo 閾値、1〜2 分）
- **Phase 2**: `scripts/with-env.sh ./mvnw -Pe2e verify`（WildFly + H2 起動 → Playwright → JaCoCo dump → 統合 95% 閾値、3〜5 分）

短サイクル開発で Phase 2 が重い場合は `HUSKY_E2E_SKIP=1 git push ...` で Phase 2 を skip 可能。ただし以下を厳守:

- skip した場合は **AI は明示的に告知すること**（暗黙の skip は禁止）
- push 前の最終確認（PR を出す前、main マージ前など）では Phase 2 を必ず実行
- skip 後の push でも CI 側 `e2e:playwright` ジョブが走るため最終 hard gate は CI 側にもある

# 制約条件

- 本ボイラープレートは社内のプライベートなネットワーク環境で利用する前提
  - mavenやnpmの公式リポジトリには接続可能だが、それ以外には接続不可能
- アプリケーションのデプロイについてはボイラープレート適用先の案件によって異なるので、本ボイラープレートではDockerイメージの作成までとする

# フォルダ構成

```
src/      # Jakarta EE アプリケーションソース（PBI #4 で再構築。現状は .gitkeep のみ）
docs/     # ドキュメント（docusaurus を利用して、GitLab Pages から確認できる）。サンプル章は PBI #4 で再構築
rules/    # Checkstyle / PMD / SpotBugs ruleset と品質担保戦略ドキュメント
scripts/  # lint-staged 用ラッパー（差分単位の Checkstyle / PMD / Spotless 起動）
README.md # 本ボイラープレートに関するREADME
```

# 作業時の注意点

- コードのコメントは日本語で記載すること
- Google Java Style に厳密に沿うこと（`rules/checkstyle/checkstyle-google.xml` は逐語コピー＋ Checker 直下 severity の 1 行パッチのみ）
  - 日本語コメント方針と Google Java Style 厳密準拠 hard gate の衝突候補は `rules/README.md` の §4.5「方針整合」節を参照
- 静的解析を回避するようなアノテーションやコメントをコード内に記載することは禁止（個別抑制が必要な場合は理由を必ずコメントで残すこと）
- READMEには本ボイラープレートが想定する（モダンな）開発サイクルに関する記載をすること
- 社内ライブラリ準拠 IF パッケージの **内容を変更しないこと**（詳細は次節を参照）
- 作業完了後は`scripts/with-env.sh ./mvnw clean verify`（本体: 単体+結合）および `scripts/with-env.sh ./mvnw -Pe2e verify`（E2E + 統合 JaCoCo 高閾値）の両方が通り、ビルドがエラーなく完了することを確認すること
- 作業完了後はcommit/pushまで実行すること

# 社内ライブラリ準拠 IF パッケージ（変更禁止）

以下のパッケージは将来「社内ライブラリ本体」に置き換わる前提の **社内ライブラリ準拠 IF** である。
本ボイラープレート上では独自実装（あるいは社内ライブラリと同等の IF を持つコピー実装）として配置しているが、
社内ライブラリ置換時の互換性確保のため、**内容変更禁止**（IF・フィールド・メソッド・スコープすべて変更不可）。
変更が必要な場合は社内ライブラリチームに相談すること。

| パッケージ                         | 役割                                                                                                                      |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `jp.mufg.it.rcb.log.cdi`           | `@InjectLogger` / `LoggerProducer` / `LoggerType`                                                                         |
| `jp.mufg.it.rcb.log.formatter`     | `RcbLogFormatter` / `RcbMessageResolver` / `RcbFormatterInstaller`（messages.properties 解決 + MDC 反映の JUL Formatter） |
| `jp.mufg.it.rcb.exception`         | `ExceptionHelper` / `ErrorCode`                                                                                           |
| `jp.mufg.it.rcb.exception.inner`   | `InnerRuntimeException` 階層 / `MSTBusinessException(NonRecover)`                                                         |
| `jp.mufg.it.rcb.exception.handler` | `FacesExceptionHandler` ほか                                                                                              |
| `jp.mufg.it.rcb.userinfo.dto`      | `UserDto` / `UserPositionDto`                                                                                             |
| `jp.mufg.it.rcb.userinfo.context`  | `UserInfoContext`（セッション保持）                                                                                       |
| `jp.mufg.it.rcb.config`            | `Config` / `ConfigFactory` / `ConfigProducer` / `PropertyId`（社内ライブラリ置換時にパッケージごと差し替わる前提）        |

これらのパッケージから呼び出されるユーティリティを新規追加する場合も、**呼び出し方向は「社内ライブラリ準拠 IF → 独自実装」の片方向のみ** とし、独自実装側から社内ライブラリ準拠 IF パッケージへの逆参照を増やさないこと。

# PBI

@PBI.md
