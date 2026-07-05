import { test, expect } from '@playwright/test';

// 認証なしタスク管理サンプルのゴールデンパス:
//   1. 新規作成画面でタイトルを入力して作成 → 一覧へ redirect。
//   2. 一覧に作成したタスクが TODO で表示される。
//   3. その行の「完了」ボタンで完了 → 一覧へ戻り DONE で表示される。
test.describe('Task management golden path', () => {
  test('create a task, see it listed, then complete it', async ({ page }) => {
    const title = `E2E ゴールデンパス ${Date.now()}`;

    await page.goto('tasks/new.xhtml');
    await page.fill('input[id="formNew:title"]', title);
    await page.click('button:has-text("作成")');
    await page.waitForURL('**/tasks/list.xhtml');

    // 作成したタスク行を特定し、状態が TODO であることを確認。
    const row = page.locator('tr', { hasText: title });
    await expect(row).toBeVisible();
    await expect(row.getByText('TODO')).toBeVisible();

    // 完了ボタンを押す（非 ajax サブミット → 一覧へ redirect）。
    await row.locator('button:has-text("完了")').click();
    await page.waitForURL('**/tasks/list.xhtml');

    // 同じタスクが DONE 表示になり、完了ボタンが消える。
    const doneRow = page.locator('tr', { hasText: title });
    await expect(doneRow.getByText('DONE')).toBeVisible();
    await expect(doneRow.locator('button:has-text("完了")')).toHaveCount(0);
  });
});
