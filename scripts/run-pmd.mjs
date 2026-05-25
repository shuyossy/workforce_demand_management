#!/usr/bin/env node
// lint-staged から呼ばれ、変更 Java ファイルに対して PMD を hard gate として実行する。
//
// --ruleset=blocking  -> rules/pmd/ruleset-blocking.xml（errorprone 厳選パターン）
// --ruleset=full      -> rules/pmd/ruleset.xml（PMD 7.x 標準 8 カテゴリ）
//
// 既定は blocking。両 ruleset とも hard gate なので違反は exit 非ゼロでコミット停止。
//
// Maven Central に shaded jar 配布がないため、pom.xml の lint-setup profile で
// `.lint-tools/lib/` 配下に pmd-cli + pmd-java + transitive を配置させた上で classpath 起動する。
// Checkstyle と同じライブラリディレクトリを共用する。

import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { readdirSync, existsSync } from 'node:fs';

const PROJECT_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const LIB = join(PROJECT_ROOT, '.lint-tools', 'lib');

const RULESETS = {
  blocking: join(PROJECT_ROOT, 'rules', 'pmd', 'ruleset-blocking.xml'),
  full: join(PROJECT_ROOT, 'rules', 'pmd', 'ruleset.xml'),
};

const positional = [];
let ruleset = 'blocking';
for (const arg of process.argv.slice(2)) {
  if (arg.startsWith('--ruleset=')) {
    ruleset = arg.slice('--ruleset='.length);
  } else {
    positional.push(arg);
  }
}

// allowed なキーだけ取り出すことで、利用側を bracket access から外し、
// eslint-plugin-security の generic-object-injection 警告を避けつつ
// `ruleset` が validated white list に含まれることも保証する。
const rulesetConfig = Object.entries(RULESETS).find(([key]) => key === ruleset)?.[1];

if (!rulesetConfig) {
  console.error(
    `[run-pmd] unknown --ruleset=${ruleset}. Expected one of: ${Object.keys(RULESETS).join(', ')}`,
  );
  process.exit(2);
}

if (positional.length === 0) process.exit(0);

if (
  !existsSync(LIB) ||
  !readdirSync(LIB).some((e) => e.startsWith('pmd-cli-') && e.endsWith('.jar'))
) {
  console.error('[run-pmd] pmd-cli lib not populated. Run: node scripts/setup-lint-tools.mjs');
  process.exit(1);
}

const classpath = process.platform === 'win32' ? `${LIB}\\*` : `${LIB}/*`;

const args = [
  '-cp',
  classpath,
  'net.sourceforge.pmd.cli.PmdCli',
  'check',
  '-R',
  rulesetConfig,
  '--no-progress',
  '-f',
  'text',
  '-d',
  ...positional,
];
const result = spawnSync('java', args, { stdio: 'inherit' });
process.exit(result.status ?? 1);
