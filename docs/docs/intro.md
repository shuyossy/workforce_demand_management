---
slug: /
title: はじめに
---

# sak-dev-env (jakartaEE ブランチ)

Jakarta EE 系列の AI 主導開発に対応したボイラープレート。WildFly 32 + PostgreSQL + Flyway + PrimeFaces による休暇申請ワークフローのサンプルアプリを内蔵している。

## 対象読者

- Jakarta EE プロジェクトの立ち上げを担当する開発者
- 本ボイラープレートを社内プロジェクトに適用するチーム
- AI 主導開発の方法論を学びたい開発者

## スコープ

- WildFly 32.0.1.Final + PostgreSQL + Flyway + PrimeFaces
- クリーン/ヘキサゴナルアーキテクチャ + ArchUnit 境界 hard gate
- 5 段の静的解析 hard gate（pre-commit）+ 全体検査（pre-push / CI）
- 単体 / 結合 / E2E (Playwright) / 性能 (k6) のテスト一式
- 社内認証サーバ（junction-path 越し）対応の URL 規約

## 次のステップ

1. [前提環境](./getting-started/prerequisites) を確認
2. [環境変数](./getting-started/env-setup) を設定
3. [初回起動](./getting-started/first-run) で F5 一発起動を試す（同梱の休暇申請サンプルが動く）
4. 適用先プロジェクトで開発を始める場合は [サンプル削除ガイド](./getting-started/strip-sample) で休暇申請サンプルを撤去し、テンプレ部だけ残してから自案件のドメインを書き始める
