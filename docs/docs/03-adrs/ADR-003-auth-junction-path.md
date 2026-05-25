# ADR-003 社内認証サーバ junction-path 対応

## コンテキスト

将来的に社内認証サーバが前段に立ち、`https://aaa/${junction-path}/rcb/...` のような URL でアプリケーションがアクセスされる。AP サーバ内部での URL 解決と外部公開 URL を一貫させる必要がある。

## 決定

3 レイヤーで対応：

1. **Undertow `proxy-address-forwarding=true`**：`X-Forwarded-*` ヘッダで外部 host / scheme を AP サーバ内に伝搬。CLI スクリプト `04-proxy-forwarding.cli` で設定
2. **コンテキストルート固定**：`jboss-web.xml` で `${env.APP_CONTEXT_ROOT:/rcb}`。junction-path の有無に関わらずアプリは常に `/rcb` 配下
3. **絶対パス禁止コード規約**：
   - 先頭スラッシュ付きリンク禁止
   - `<h:link>` / `<h:button>` / `<h:outputStylesheet>` 等 JSF コンポーネント必須
   - 生 `<a>` が必要なら `#{request.contextPath}/…`
   - 絶対 URL が必要な場合（メール本文等）は `shared.web.AppUrlBuilder` に集約、`APP_EXTERNAL_BASE_URL` で上書き

## 結果

- junction-path が変わっても AP サーバ内部の URL 解決が一貫
- 認証サーバ側で URL 書き換えがあっても破綻しない
- 規約違反は ArchUnit / コードレビューで検出（PMD では検出困難）

## 代替案

- **認証サーバ側で URL 書き換え**：適用先環境の認証サーバ機能に依存。常に使えるとは限らない。却下
