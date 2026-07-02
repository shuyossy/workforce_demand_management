import { test, expect, Page } from '@playwright/test';

// 業務エラー（回復可能: MSTBusinessException）が JSF ライフサイクル経由で
// 正しくハンドリングされ、「現画面に留まって FacesMessage を表示する」ことを担保する。
//
// この経路（FacesExceptionHandlerFactory → FacesExceptionHandler →
// ExceptionFacesResponseHandler）は、プロジェクトのテスト戦略上 E2E でしか
// 実通過できない（単体は throw までで止まり、結合テストはコンテナを起動しない）。
//
// 検証シナリオ（stale 状態＝二重処理）:
//   1. 申請者 E0001 が固有期間で新規申請し、その申請の詳細 URL を取得する。
//   2. 承認権者 E0002 を 2 セッション（別コンテキスト）で起動し、両方が同じ
//      PENDING の詳細画面を開く（承認ボタンが描画される）。
//   3. 一方（B）が先に承認 → 申請は APPROVED に遷移する。
//   4. もう一方（A）が stale な画面で承認をクリックする。
//      → Service が「申請中のみ承認/却下できます」で MSTBusinessException を送出。
//
// 期待挙動（設計が正）:
//   - A は詳細画面（detail.xhtml）に留まる（error.xhtml へ全画面遷移しない）。
//   - 業務エラーメッセージが p:messages に表示される。

async function login(page: Page, empNum: string): Promise<void> {
  await page.goto('login.xhtml');
  await page.selectOption('select[id="loginForm:user_input"]', empNum, { force: true });
  await page.click('button:has-text("ログイン")');
  await page.waitForURL('**/leaves/list.xhtml');
}

test.describe('Business error handling (JSF exception handler wiring)', () => {
  test('approving an already-processed request stays on detail with a business message (not error.xhtml)', async ({
    browser,
  }) => {
    // list.xhtml は期間を LocalDate の ISO 形式（YYYY-MM-DD）で表示するため、
    // 行特定は ISO 形式で、日付入力（p:datePicker）はスラッシュ形式で行う。
    const startIso = '2026-09-21';

    // --- 1. 申請者 E0001 が固有期間で新規申請し、詳細 URL を取得 ---
    const applicantCtx = await browser.newContext();
    const applicant = await applicantCtx.newPage();
    await login(applicant, 'E0001');

    await applicant.goto('leaves/new.xhtml');
    await applicant.selectOption('select[id="formNew:type_input"]', 'PAID', { force: true });
    await applicant.fill('input[id="formNew:start_input"]', '2026/09/21');
    await applicant.fill('input[id="formNew:end_input"]', '2026/09/22');
    await applicant.fill('textarea[id="formNew:reason"]', 'E2E 例外ハンドリング（stale 状態）検証');
    await applicant.click('button:has-text("申請")');
    await applicant.waitForURL('**/leaves/list.xhtml');

    // 「自分の申請」タブ（appliedAt DESC で先頭が最新）から対象行の詳細を開く。
    await applicant
      .locator('tr', { hasText: startIso })
      .locator('a:has-text("詳細")')
      .first()
      .click();
    await applicant.waitForURL('**/leaves/detail.xhtml**');
    const detailUrl = applicant.url();
    expect(detailUrl).toContain('detail.xhtml?id=');

    // --- 2. 承認権者 E0002 を 2 セッションで起動し、両方が PENDING 詳細を開く ---
    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await login(pageA, 'E0002');
    await pageA.goto(detailUrl);
    // 承認ボタンが描画される時点（＝この時点では承認可能）を確認。
    await expect(pageA.locator('button:has-text("承認")')).toBeVisible();

    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await login(pageB, 'E0002');
    await pageB.goto(detailUrl);

    // --- 3. B が先に承認 → 申請は APPROVED に遷移 ---
    await pageB.fill('textarea[id="detailForm:comment"]', '先に承認');
    await pageB.click('button:has-text("承認")');
    await pageB.waitForURL('**/leaves/list.xhtml');

    // --- 4. A が stale な画面で承認 → 業務エラー ---
    await pageA.fill('textarea[id="detailForm:comment"]', '後追い承認');
    await pageA.click('button:has-text("承認")');

    // === 期待挙動（設計が正）===
    // 現画面（detail.xhtml）に留まり、error.xhtml へ全画面遷移しない。
    await expect(pageA).toHaveURL(/leaves\/detail\.xhtml/);
    // 業務エラーメッセージが表示される。
    await expect(pageA.getByText('申請中の休暇申請のみ承認/却下できます')).toBeVisible();
    // システムエラー画面（error.xhtml）には遷移していない。
    await expect(pageA.getByText('エラーが発生しました')).toHaveCount(0);

    await applicantCtx.close();
    await ctxA.close();
    await ctxB.close();
  });

  // メッセージ表示は各フォームの p:messages を廃し、テンプレート main.xhtml の
  // globalMessages に一本化した（二重表示防止）。この変更で「必須検証エラーが
  // 消えずに一本化先へ表示される」ことを回帰防止として担保する。
  test('required-field validation errors are shown once via the consolidated global messages (new.xhtml)', async ({
    page,
  }) => {
    await login(page, 'E0001');
    await page.goto('leaves/new.xhtml');

    // 何も入力せず申請 → JSF の required 検証エラーで申請画面に留まる。
    await page.click('button:has-text("申請")');

    await expect(page).toHaveURL(/leaves\/new\.xhtml/);
    // 一本化した globalMessages にエラーが描画される（PrimeFaces の error スタイル）。
    await expect(page.locator('.ui-messages-error').first()).toBeVisible();
  });
});
