#!/usr/bin/env node
// lint-staged から呼ばれ、変更 Java ファイルに対して Checkstyle を hard gate として実行する。
//
// --ruleset=blocking  -> rules/checkstyle/checkstyle-blocking.xml（プロジェクト独自致命パターン）
// --ruleset=google    -> rules/checkstyle/checkstyle-google.xml（google_checks 全規則 + severity=error 1 行パッチ）
//
// 既定は blocking。両 ruleset とも hard gate なので違反は exit 非ゼロでコミット停止。
//
// Maven Central に shaded jar 配布がないため、pom.xml の lint-setup profile で
// `.lint-tools/lib/` 配下に checkstyle + transitive を配置させた上で classpath 起動する。

import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';

const PROJECT_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const LIB = join(PROJECT_ROOT, '.lint-tools', 'lib');
const SUPPRESSIONS = join(PROJECT_ROOT, 'rules', 'checkstyle', 'suppressions.xml');

const RULESETS = {
  blocking: join(PROJECT_ROOT, 'rules', 'checkstyle', 'checkstyle-blocking.xml'),
  google: join(PROJECT_ROOT, 'rules', 'checkstyle', 'checkstyle-google.xml'),
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
    `[run-checkstyle] unknown --ruleset=${ruleset}. Expected one of: ${Object.keys(RULESETS).join(', ')}`,
  );
  process.exit(2);
}

if (positional.length === 0) {
  process.exit(0);
}

if (
  !existsSync(LIB) ||
  !readdirSync(LIB).some((e) => e.startsWith('checkstyle-') && e.endsWith('.jar'))
) {
  console.error(
    '[run-checkstyle] checkstyle lib not populated. Run: node scripts/setup-lint-tools.mjs',
  );
  process.exit(1);
}

const classpath = process.platform === 'win32' ? `${LIB}\\*` : `${LIB}/*`;

const result = spawnSync(
  'java',
  [
    // google_checks.xml が参照する独自プロパティに suppressions ファイルを渡す
    // （blocking ruleset は当該プロパティを参照しないので無害）。
    `-Dorg.checkstyle.google.suppressionfilter.config=${SUPPRESSIONS}`,
    '-cp',
    classpath,
    'com.puppycrawl.tools.checkstyle.Main',
    '-c',
    rulesetConfig,
    ...positional,
  ],
  { stdio: 'inherit' },
);
process.exit(result.status ?? 1);
