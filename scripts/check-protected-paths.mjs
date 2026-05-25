#!/usr/bin/env node
// pre-commit から呼ばれ、社内ライブラリ準拠 IF パッケージへの変更を検出して commit をブロックする。
// AGENTS.md §「社内ライブラリ準拠 IF パッケージ（変更禁止）」記載のパッケージ群を保護対象とし、
// 将来「社内ライブラリ本体」に置き換わる前提のコードを誤って改変・拡張させないためのガード。
//
// 動作:
//   - `git diff --cached --name-only --diff-filter=AMDR` の staged 変更を取得
//   - PROTECTED_PREFIXES のいずれかに前方一致したファイルがあれば、列挙して exit 1
//   - マッチなしの場合は無音で exit 0
//
// バイパス: `git commit --no-verify`。ただし PR 説明欄に変更理由の明記必須（運用ルール）。

import { spawnSync } from 'node:child_process';

const PROTECTED_PREFIXES = [
  'src/main/java/jp/mufg/it/rcb/log/cdi/',
  'src/main/java/jp/mufg/it/rcb/log/formatter/',
  'src/main/java/jp/mufg/it/rcb/exception/',
  'src/main/java/jp/mufg/it/rcb/userinfo/dto/',
  'src/main/java/jp/mufg/it/rcb/userinfo/context/',
  'src/main/java/jp/mufg/it/rcb/config/',
];

const result = spawnSync('git', ['diff', '--cached', '--name-only', '--diff-filter=AMDR'], {
  encoding: 'utf8',
});

if (result.status !== 0) {
  console.error('[check-protected-paths] git diff --cached failed:', result.stderr);
  process.exit(result.status ?? 1);
}

const staged = (result.stdout ?? '')
  .split('\n')
  .map((s) => s.trim())
  .filter(Boolean);

const violations = staged.filter((path) =>
  PROTECTED_PREFIXES.some((prefix) => path.startsWith(prefix)),
);

if (violations.length === 0) {
  process.exit(0);
}

console.error('\n[check-protected-paths] 社内ライブラリ準拠 IF パッケージへの変更を検出しました。');
console.error(
  '  これらのパッケージは将来「社内ライブラリ本体」に置き換わる前提のため、内容変更（enum 値 / メソッド / フィールド / クラス追加を含む）は禁止です。',
);
console.error(
  '  詳細は AGENTS.md §「社内ライブラリ準拠 IF パッケージ（変更禁止）」を参照してください。\n',
);
console.error('  変更検出ファイル:');
for (const path of violations) {
  console.error(`    - ${path}`);
}
console.error(
  '\n  緊急時は `git commit --no-verify` で迂回可能ですが、PR 説明に変更理由を明記してください。\n',
);

process.exit(1);
