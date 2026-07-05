# 非機能要件

## 性能

- k6 スモーク（VUs=5 / duration=30s / p95 < 1000ms）。回帰検知のみ
- 本格的な性能要件は適用先プロジェクトで定義する想定

## 可用性

- 本サンプルは単一インスタンス前提
- 冗長化（ロードバランサ / セッションレプリケーション等）は適用先で対応

## 保守性

- カバレッジ閾値（JaCoCo `<rule>`、CI で hard gate）

  | 系統                                       | LINE | BRANCH |
  | ------------------------------------------ | ---- | ------ |
  | 本体（`-Pfast` / `-Pci-mr` / `-Pci-main`） | 85%  | 85%    |
  | `-Pe2e`                                    | 95%  | 95%    |

- アプリ側パッケージ全体を `element=BUNDLE` 1 本でフラット評価する。社内ライブラリ準拠 IF パッケージ（`config` / `log.cdi` / `log.formatter` / `exception.**` / `userinfo.**`）は plugin-level `<excludes>` で常に対象外
- クラス単位の例外は ADR-004 の例外台帳で根拠を一元管理する
- アダプタ層（Repository / Backing Bean）は **結合テストで担保**：本物の Service / 本物の Repository / 本物の H2 DB まで通すバッキングビーン IT を Failsafe で実行する。バッキングビーンの単体テスト（Service モック）は冗長なので行わない。CDI 配線と JSF ライフサイクルの正しさは Playwright E2E が担保する

詳細は仕様書 §8 / ADR-004 参照。

## セキュリティ

- 本サンプルは認証機能を持たない（ログイン画面・セッション認証・認可判定を持たない、匿名の単一利用者を前提とした最小構成）
- 将来、社内認証サーバ前提（junction-path 越し、`X-User-Id` ヘッダ受領）の認証・認可を追加する場合の URL 規約は ADR-003 に定義済み（詳細は [技術設計書](../technical-design) §6 / §7）

## ロギング

- 標準出力（コンテナ前提）
- MDC で `requestId`（UUID 短縮）と `empNum` をリクエスト境界で格納、レスポンス完了時 `MDC.clear`
- フォーマット：`[%X{requestId}][%X{empNum}] %message`
- 業務エラーの自動ログは `MSTBusinessNonRecoverException`（回復不可）のみ ERROR で出力（`MST00004-E`）。`MSTBusinessException`（回復可）はユーザへの `FacesMessage` で完結し、フレームワーク自動ログを行わない。UseCase 側は自動ログとの重複出力をしない（詳細は技術設計書 §3 / §4 参照）
