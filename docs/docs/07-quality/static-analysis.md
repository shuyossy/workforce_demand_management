# 静的解析

## 原則

- 「警告が出るから消す」を禁止。**必ず構造側で対応するか、抑制範囲を最小に絞る**
- 抑制はパッケージ・クラス・メソッド単位の最小範囲で
- 抑制時は **`rules/README.md` §4.5 決定ログに必須記録**：日時、対象、衝突ルール、検討した代替案、採択理由
- Google Java Style 規則の Checker 直下 severity は変更しない（逐語コピー原則）
- 衝突が広範に及ぶ場合は `rules/checkstyle/checkstyle-project.xml` を新規追加し override

## 5 段 hard gate

pre-commit で差分ファイルに対し以下の 5 段を順次実行：

| 順序 | ツール              | 対象 / ruleset                                                       | 失敗時   |
| ---- | ------------------- | -------------------------------------------------------------------- | -------- |
| 1    | Spotless            | eclipse-formatter（Google 公式版）                                   | ブロック |
| 2    | Checkstyle blocking | `rules/checkstyle/checkstyle-blocking.xml`（最重要違反）             | ブロック |
| 3    | Checkstyle google   | `rules/checkstyle/checkstyle-google.xml`（Google Java Style 全規則） | ブロック |
| 4    | PMD blocking        | `rules/pmd/ruleset-blocking.xml`（最重要違反）                       | ブロック |
| 5    | PMD full            | `rules/pmd/ruleset.xml`（PMD 7.x 標準 8 カテゴリ）                   | ブロック |

## pre-push / CI

- **pre-push**：SpotBugs + JUnit + JaCoCo（差分単位化困難なため全体実行）
- **CI**：pre-commit と pre-push の全段 + ArchUnit + 層別 JaCoCo 閾値検証 + `mvn site` レポート

## 衝突時の決定フロー

1. まず構造側（コード、パッケージ分割、helper 抽出）で対応できるか検討
2. できなければ rule property（`<property name="...">`）で閾値調整
3. それでも無理なら exclude（パッケージ / クラス限定）
4. 個別 `@SuppressWarnings` は最終手段。理由コメント必須
5. 決定内容は `rules/README.md` §4.5 決定ログに記録

詳細手順は `rules/README.md` を参照。

## 静的解析の最適化対象

以下の構造的衝突については、ボイラープレートとしての標準対処方針を採用済み：

| 領域                                     | 想定衝突                                                          | 方針                                                                                      |
| ---------------------------------------- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Lombok                                   | PMD/CheckStyle の自動生成コード警告                               | `lombok.config` で `@SuppressWarnings("all")` 付与                                        |
| JPA Entity                               | PMD `DataClass`、SpotBugs `EI_EXPOSE_REP`                         | `adapter.out.persistence.jpa` パッケージ限定で suppress                                   |
| CDI Bean                                 | SpotBugs `SE_BAD_FIELD`（@SessionScoped で非 Serializable）       | `@SessionScoped` 限定で pattern exclude                                                   |
| JSF Backing Bean                         | PMD `AvoidFieldNameMatchingMethodName`                            | `adapter.in.web` 配下のみ緩和                                                             |
| Bean Validation Command                  | PMD `TooManyFields`、`ImmutableField`                             | Command は immutable record か Lombok `@Value`、`TooManyFields` 閾値を 15 に              |
| テストコード                             | PMD `JUnitTestContainsTooManyAsserts`、CheckStyle メソッド名長    | `src/test/java` 専用 ruleset で緩和                                                       |
| ArchUnit テスト                          | PMD `ExcessiveImports`、CheckStyle クラス長                       | `ArchitectureTest.java` ファイル限定で `@SuppressWarnings` ＋ 理由コメント                |
| メッセージID 定数集                      | PMD `TooManyStaticImports`、CheckStyle ファイル長                 | sealed interface + nested class で構造側対処                                              |
| 社内ライブラリ準拠 IF DTO のチェーン参照 | PMD `LawOfDemeter`（`dto.getXxxDto().getYyy()` のような連鎖呼出） | 社内ライブラリ準拠 IF DTO は変更不可のため、利用側で `@SuppressWarnings` の個別抑制を許容 |

詳細は `rules/README.md` §4.5 決定ログ参照。
