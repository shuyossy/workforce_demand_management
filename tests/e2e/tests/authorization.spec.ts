import { test, expect } from '@playwright/test';

// 認可ガードシナリオ:
//   1) 部下層 (L1) は承認操作を実行できない（detail 画面に承認ボタンが出ない）
//   2) 部長層 (M1) でも自分の申請は承認できない（自己承認禁止: isSelf=true で canApprove=false）
// 目的: AuthenticationFilter の skip 判定 + FindLeaveService.canApprove
//      （!isSelf && isPending && authorized）の各分岐を E2E で実通過させる。
test.describe('Leave Approval Authorization Guards', () => {
  test('L1 (E0001) cannot see approve action on others requests', async ({ page }) => {
    // E0001 (L1, ORG-001) でログイン
    await page.goto('login.xhtml');
    await page.selectOption('select[id="loginForm:user_input"]', 'E0001', { force: true });
    await page.click('button:has-text("ログイン")');
    await page.waitForURL('**/leaves/list.xhtml');

    // 承認待ちタブが見える場合に開く（L1 は権限なしのため、自分以外の申請の承認ボタンが見えない）
    // 自分の申請タブにも詳細リンクが残るため、:visible でフィルタして
    // 切替後の active tab に存在する詳細リンクのみ拾う。
    const approveTab = page.locator('a:has-text("承認待ち")');
    if ((await approveTab.count()) > 0) {
      await approveTab.click();
      const detailLinks = page.locator('a:has-text("詳細"):visible');
      if ((await detailLinks.count()) > 0) {
        await detailLinks.first().click();
        await expect(page.locator('button:has-text("承認")')).toHaveCount(0);
      }
    }
  });

  test('M1 (E0002) cannot approve own request (self-approval blocked)', async ({ page }) => {
    // E0002 (M1, ORG-001) でログイン → 自己申請
    await page.goto('login.xhtml');
    await page.selectOption('select[id="loginForm:user_input"]', 'E0002', { force: true });
    await page.click('button:has-text("ログイン")');
    await page.waitForURL('**/leaves/list.xhtml');

    await page.goto('leaves/new.xhtml');
    await page.selectOption('select[id="formNew:type_input"]', 'PAID', { force: true });
    await page.fill('input[id="formNew:start_input"]', '2026/08/01');
    await page.fill('input[id="formNew:end_input"]', '2026/08/02');
    await page.fill('textarea[id="formNew:reason"]', 'E2E 自己承認禁止テスト');
    await page.click('button:has-text("申請")');
    await page.waitForURL('**/leaves/list.xhtml');

    // 自分の申請（PENDING）の detail 画面では承認・却下ボタンが表示されない。
    // list.xhtml は reason 列を持たないため期間日付（LocalDate toString = ISO 形式）で照合する。
    const ownRequestRow = page.getByText('2026-08-01').first();
    await expect(ownRequestRow).toBeVisible();
    // 自分タブで PENDING の自己申請の「詳細」リンクをクリック。
    // 承認待ちタブにも詳細リンクが残る可能性があるため :visible でフィルタ。
    await page.locator('a:has-text("詳細"):visible').first().click();
    await expect(page.locator('button:has-text("承認")')).toHaveCount(0);
    await expect(page.locator('button:has-text("却下")')).toHaveCount(0);
  });
});
