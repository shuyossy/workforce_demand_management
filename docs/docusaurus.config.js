export default {
  title: 'sak-dev-env (jakartaEE branch)',
  tagline:
    'Jakarta EE / WildFly 向け開発環境ボイラープレート（休暇申請ワークフローのサンプルを内蔵）',
  favicon: 'img/favicon.ico',
  url: process.env.CI_PAGES_URL ?? 'http://localhost:3000',
  baseUrl: process.env.CI_PAGES_URL ? new URL(process.env.CI_PAGES_URL).pathname : '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  i18n: {
    defaultLocale: 'ja',
    locales: ['ja'],
  },
  // mermaid 図表（アーキテクチャ / シーケンス）を md 内で直接書けるように
  markdown: { mermaid: true },
  themes: ['@docusaurus/theme-mermaid'],
  presets: [
    [
      'classic',
      {
        docs: { sidebarPath: './sidebars.js' },
        blog: false,
        theme: { customCss: './src/css/custom.css' },
      },
    ],
  ],
};
