# ADR-005 JS バンドルなし方針

## コンテキスト

JSF / PrimeFaces アプリで JavaScript バンドラ（webpack / vite）を使うかどうかの選択。バンドラは SPA 構成では標準だが、JSF はサーバサイドレンダリング前提で、JS は補助的な役割。

## 決定

JS バンドラを **使わない**。

- 各 xhtml が必要な JS を `<h:outputScript>` で個別読込
- 共通スクリプトは `resources/js/common.js`
- 画面固有 JS は `resources/js/{page}.js`

## 結果

- ビルド単純（`frontend-maven-plugin` の役割は docs ビルドと E2E (Playwright) の導入のみに絞られる）
- 学習コスト低（追加の JS ビルド設定が不要）
- JS が複雑化したら本 ADR を見直す

## 代替案

- **vite で個別 JS をバンドル**：JSF の慣習から外れ、学習コストと複雑度が増す。本サンプルの「最もシンプル」要件と整合しない。却下
