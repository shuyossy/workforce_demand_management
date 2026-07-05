import { test, expect } from '@playwright/test';

// 例外処理からのページ遷移を JSF ライフサイクル経由で実通過させる（本プロジェクトでは E2E でしか通らない経路）。
test.describe('Exception handling (JSF exception handler wiring)', () => {
  // (a) バリデーションエラー: タイトル未入力 → 現画面(new)に留置 + globalMessages。
  test('required-field validation stays on new.xhtml with a global message', async ({ page }) => {
    await page.goto('tasks/new.xhtml');
    await page.click('button:has-text("作成")');
    await expect(page).toHaveURL(/tasks\/new\.xhtml/);
    await expect(page.locator('.ui-messages-error').first()).toBeVisible();
  });

  // (b) 業務エラー(回復可): 2 タブ stale 二重完了 → 現画面(list)に留置 + 業務メッセージ、error.xhtml へ遷移しない。
  test('double-complete stays on list with a business message (not error.xhtml)', async ({
    browser,
  }) => {
    const title = `E2E 二重完了 ${Date.now()}`;

    // 事前に 1 件作成。
    const setup = await browser.newContext();
    const setupPage = await setup.newPage();
    await setupPage.goto('tasks/new.xhtml');
    await setupPage.fill('input[id="formNew:title"]', title);
    await setupPage.click('button:has-text("作成")');
    await setupPage.waitForURL('**/tasks/list.xhtml');
    await setup.close();

    // 2 セッションで同じ一覧（TODO 完了ボタンが見える stale 状態）を開く。
    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await pageA.goto('tasks/list.xhtml');
    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await pageB.goto('tasks/list.xhtml');

    // B が先に完了。
    await pageB.locator('tr', { hasText: title }).locator('button:has-text("完了")').click();
    await pageB.waitForURL('**/tasks/list.xhtml');

    // A が stale な画面で完了 → 業務エラー（回復可）。
    await pageA.locator('tr', { hasText: title }).locator('button:has-text("完了")').click();

    await expect(pageA).toHaveURL(/tasks\/list\.xhtml/);
    await expect(pageA.getByText('既に完了したタスクです')).toBeVisible();
    await expect(pageA.getByText('エラーが発生しました')).toHaveCount(0);

    await ctxA.close();
    await ctxB.close();
  });

  // (c) 業務エラー(回復不可): 存在しない ID への完了 POST 偽装 → faces-config 経由で error.xhtml に遷移。
  test('completing a non-existent id (forged POST) redirects to error.xhtml', async ({ page }) => {
    // まず TODO を 1 件用意（完了ボタンが描画される状態を作る）。
    const title = `E2E 偽装 ${Date.now()}`;
    await page.goto('tasks/new.xhtml');
    await page.fill('input[id="formNew:title"]', title);
    await page.click('button:has-text("作成")');
    await page.waitForURL('**/tasks/list.xhtml');

    // 完了サブミットの POST ボディで taskId を存在しない値に書き換える（POST 偽装の再現）。
    // ViewState 等はそのままに taskId の値のみ差し替えるため JSF ライフサイクルは正常に進み、
    // UseCase が対象不存在を検知して MSTBusinessNonRecoverException を送出する。
    await page.route('**/tasks/list.xhtml', async (route) => {
      const req = route.request();
      if (req.method() === 'POST') {
        const body = req.postData() ?? '';
        const forged = body.replace(/taskId=\d+/, 'taskId=999999999');
        await route.continue({ postData: forged });
      } else {
        await route.continue();
      }
    });

    await page.locator('tr', { hasText: title }).locator('button:has-text("完了")').click();

    // faces-config の error.page.500 経由で error.xhtml に全画面遷移する。
    await expect(page).toHaveURL(/error\.xhtml/);
    await expect(page.getByText('エラーが発生しました')).toBeVisible();
    // 戻りリンクからタスク一覧へ戻れる。
    await page.click('a:has-text("タスク一覧へ戻る")');
    await expect(page).toHaveURL(/tasks\/list\.xhtml/);
  });
});
