import js from '@eslint/js';
import security from 'eslint-plugin-security';
import prettier from 'eslint-config-prettier';
import globals from 'globals';

// 本ボイラープレートは xhtml + 各ファイル個別 JS バインドの構成のため、フロントエンドの
// バンドル設定（vite / vitest 等）は持たない。Docusaurus 用設定、Node スクリプト用設定、
// k6 性能テスト用設定の 3 つのスコープを定義する。
export default [
  {
    // docs/node_modules, docs/build, docs/.docusaurus はビルド生成物・依存物。
    // target / node_modules はルート側の生成物。
    ignores: [
      'docs/node_modules/**',
      'docs/build/**',
      'docs/.docusaurus/**',
      'node_modules/**',
      'target/**',
    ],
  },
  js.configs.recommended,
  security.configs.recommended,
  {
    // docs/ 配下は独立した Docusaurus プロジェクト（Node スコープ + React/JSX）。
    // Node/Browser グローバルと JSX パースを許可する（Docusaurus の推奨スキャフォールドに準拠）。
    files: ['docs/**/*.{js,mjs,cjs}'],
    languageOptions: {
      ecmaVersion: 2024,
      sourceType: 'module',
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
      globals: { ...globals.node, ...globals.browser },
    },
    rules: {
      // Docusaurus 雛形は React コンポーネント（JSX）を含む。
      // eslint-plugin-react を導入していないため、JSX 内で参照される識別子を
      // ESLint が検出できず no-unused-vars が誤検出する。雛形範囲では無効化する。
      'no-unused-vars': 'off',
    },
  },
  {
    // scripts/ 配下は lint-staged から起動される Node スクリプト群。
    // fileURLToPath 等の Node API と process/console グローバルを使う。
    files: ['scripts/**/*.{js,mjs,cjs}', '*.config.js', 'commitlint.config.js'],
    languageOptions: {
      ecmaVersion: 2024,
      sourceType: 'module',
      globals: { ...globals.node },
    },
  },
  {
    // tests/perf/ 配下は k6 スクリプト。k6 ランタイムが `__ENV` 等の特殊
    // グローバルを提供し、`k6/*` モジュールは k6 バイナリが内蔵で解決する。
    // Node.js ではなく k6 ランタイムで実行されるため、独立したスコープで
    // パース・グローバルを定義する。
    files: ['tests/perf/**/*.js'],
    languageOptions: {
      ecmaVersion: 2024,
      sourceType: 'module',
      globals: {
        __ENV: 'readonly',
        __VU: 'readonly',
        __ITER: 'readonly',
      },
    },
  },
  prettier,
];
