import React from 'react';
import Layout from '@theme/Layout';

export default function Home() {
  return (
    <Layout title="Home" description="sak-dev-env (jakartaEE branch) docs">
      <main style={{ padding: '2rem' }}>
        <h1>sak-dev-env (jakartaEE branch)</h1>
        <p>Jakarta EE / WildFly 向け開発環境ボイラープレートのドキュメントです。</p>
        <p>
          休暇申請ワークフローのサンプルアプリを内蔵し、要件定義 / ADR / 技術設計 / 運用 /
          品質の各章を提供します。
        </p>
        <p>
          <a href="./docs/intro">ドキュメントを読む</a>
        </p>
      </main>
    </Layout>
  );
}
