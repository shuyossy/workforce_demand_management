# ADR-001 クリーン/ヘキサゴナル採用と単一モジュール構成

## コンテキスト

Jakarta EE サンプルでクリーン/ヘキサゴナルアーキテクチャを示す必要があるが、ボイラープレートとして **最もシンプル** であることも同時に求められる。Maven マルチモジュール化は学習教材としてもボイラープレート利用者にとってもオーバー。

## 決定

- 単一 Maven モジュールを採用
- 層分離は **パッケージ** で表現（`domain` / `application` / `adapter`）
- 層境界は **ArchUnit テスト** で hard gate（コンパイル時に弾けない代わり）

詳細：

- `..domain..` は `java..` / `lombok..` 以外に依存しない（Persistence Confinement / Scope Annotation Confinement 含む）
- `..application..` は `..adapter..` / `jakarta.persistence..` / `jakarta.faces..` に依存しない
- `..adapter.in..` は `..adapter.out..` に直接依存しない（port 経由必須）

## 結果

- ビルドが単純で IDE も軽い
- 教育価値（クリーンアーキを最小コードで示す）あり
- 境界違反はコンパイル時には弾けないが、ArchUnit が CI で hard gate するため実質的に等価

## 代替案

- **Maven マルチモジュール化**：境界違反をコンパイル時に弾けるが、サンプルとしてオーバー（モジュール間 dep 管理コストも増）。却下
- **JPA Entity をドメインモデルとして使う実用クリーン**：マッピングコードが減るが DDD としての純度が下がり、`..domain..` が `jakarta.persistence..` に依存してしまう。却下

## サンプル例での履歴: 単一 bounded context の維持

:::note サンプル例
本セクションは休暇申請サンプルでの判断経緯。適用先プロジェクトで業務サブドメインの構成が異なる場合は本セクションごと書き換える（→ [サンプル削除ガイド](../getting-started/strip-sample)）。
:::

休暇申請サンプル（初版）では業務サブドメインを `jp.mufg.it.rcb.leave` 配下に括っていたが、休暇申請は単一 bounded context として十分小さく、追加の業務サブドメインを抱える計画もないため、中間 `leave` 階層は YAGNI と判断し `domain` / `application` / `adapter` をルートパッケージ（`jp.mufg.it.rcb`）直下に配置する構成へ移行した（PBI #4 指摘対応）。

### 判断基準（再評価トリガ）

業務サブドメインが 2 つ以上に増えた時点で本 ADR を再評価し、`contextA` / `contextB` のような中間階層の再導入を検討する。具体的なトリガ：

- 複数 PBI 横断でドメインモデルが衝突する（同名 Entity が別意味で必要等）
- 永続化ユニットを業務サブドメイン単位で分割したくなる
- 開発チームを業務サブドメイン単位で分けたくなる

### 影響

- ArchUnit ルールは `..domain..` / `..application..` / `..adapter..` をルート相対で参照する
- JaCoCo include / SpotBugs exclude / persistence.xml の永続化ユニット名（`appPU` / `appTestPU`）を同調更新済み
- `config` / `log.formatter` の confinement ルールは「アプリ側 4 階層（`domain` / `application` / `adapter` / `shared`）を明示列挙」に書き換え、社内ライブラリ準拠 IF パッケージ間の依存（例: `log.cdi` → `config`）を誤検知しないようにする
