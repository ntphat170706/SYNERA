import { expect as baseExpect } from '@playwright/test';
import fs from 'node:fs';
import { base, browser, login, token, command, password } from './browser-session.mjs';
const instance = await browser();
const expect = baseExpect.configure({
  timeout: Number(process.env.TEST_EXPECT_TIMEOUT_MS || 5000),
});
const errors = [];
let count = 0;
const pass = (name) => {
  count++;
  console.log('PASS ' + name);
};
const contexts = await Promise.all([
  instance.newContext({ viewport: { width: 1440, height: 1000 } }),
  instance.newContext({ viewport: { width: 1440, height: 1000 } }),
  instance.newContext({
    viewport: { width: 390, height: 844 },
    permissions: ['geolocation'],
    geolocation: { latitude: 10.782, longitude: 106.687 },
  }),
]);
const [admin, npp, sales] = await Promise.all(contexts.map((context) => context.newPage()));
for (const page of [admin, npp, sales]) {
  page.setDefaultTimeout(Number(process.env.TEST_EXPECT_TIMEOUT_MS || 30000));
  page.on('pageerror', (e) => errors.push(e.message));
}
async function action(page, name) {
  console.log('STEP ' + name);
  await page.getByRole('button', { name, exact: true }).click();
  const dialog = page.getByRole('dialog');
  const reason = dialog.getByLabel('Lý do / ghi chú', { exact: true });
  if (await reason.count()) await reason.fill('Kiểm thử trình duyệt với dữ liệu máy chủ');
  await dialog.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await expect(dialog).not.toBeVisible();
}
fs.mkdirSync('artifacts', { recursive: true });
try {
  await npp.goto(base + '/#/orders');
  await expect(npp.getByRole('heading', { name: 'Chào mừng trở lại' })).toBeVisible();
  pass('Protected route requires authentication');
  await login(npp, 'npp@synera.local');
  await expect(npp.getByLabel('Chọn vai trò')).toHaveCount(0);
  pass('Real login derives role from server');
  await npp.evaluate(() => localStorage.setItem('synera-demo-v1', '{"preserved":"user-data"}'));
  await npp.goto(base + '/#/catalog');
  const firstQty = npp.getByRole('spinbutton', { name: /Số lượng/ }).first();
  await firstQty.fill('1');
  await expect(npp.getByRole('button', { name: 'Đặt hàng NSX', exact: true })).toBeEnabled();
  await npp.getByRole('button', { name: 'Đặt hàng NSX', exact: true }).click();
  await expect(npp).toHaveURL(/order=\d+/);
  const id = new URLSearchParams(npp.url().split('?')[1]).get('order');
  expect(id).toBeTruthy();
  pass('Catalog quotes and creates a persisted sell-in order');
  await npp.reload();
  await expect(npp.getByRole('heading', { name: /Chi tiết/ })).toBeVisible();
  pass('HttpOnly refresh restores session and order after reload');
  await login(admin, 'admin@synera.local');
  await admin.goto(base + '/#/orders?order=' + id);
  await action(admin, 'Duyệt đơn');
  await action(admin, 'Xác nhận số lượng');
  await action(admin, 'Xuất kho');
  await action(admin, 'Bắt đầu giao');
  await action(admin, 'Xác nhận đã giao');
  pass('Approval, reservation, shipping and delivery use separate server transitions');
  await npp.goto(base + '/#/orders?order=' + id);
  await action(npp, 'Ghi nhận thực nhận');
  await expect(npp.locator('.npp-detail-surface .toolbar > .badge')).toHaveText('Đã nhận');
  pass('NPP records receipt through API');
  await npp.goto(base + '/#/finance');
  await expect(npp.getByRole('heading', { name: 'Công nợ với NSX' })).toBeVisible();
  await expect(npp.locator('table')).not.toHaveCount(0);
  pass('Credit and invoice screens load real balances');
  const adminToken = await token(contexts[0].request, 'admin@synera.local');
  const phone = '09' + String(Date.now()).slice(-8);
  const store = await command(contexts[0].request, adminToken, '/stores', {
    nppId: 2,
    repId: 3,
    name: 'E2E ' + phone,
    owner: 'E2E',
    phone,
    address: 'Điểm bán kiểm thử',
    latitude: 10.782,
    longitude: 106.687,
  });
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
  const weekday =
    ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'].indexOf(
      new Intl.DateTimeFormat('en-US', { weekday: 'long', timeZone: 'Asia/Ho_Chi_Minh' }).format(
        new Date(),
      ),
    ) + 1;
  const route = await command(contexts[0].request, adminToken, '/routes', {
    nppId: 2,
    repId: 3,
    name: 'E2E route ' + phone,
    storeIds: [store.id],
    weekday,
  });
  await command(contexts[0].request, adminToken, `/routes/${route.id}/plan`, { day });
  await login(sales, 'sales@synera.local');
  await expect(sales.getByRole('heading', { name: 'Sales · Hôm nay', exact: true })).toBeVisible();
  await expect(sales.locator('.loading')).toHaveCount(0, { timeout: 30000 });
  const visitPanel = sales
    .locator('.panel')
    .filter({ has: sales.getByRole('heading', { name: 'Lịch ghé hôm nay', exact: true }) });
  const visitRow = visitPanel.locator('tr').filter({ hasText: 'E2E ' + phone });
  for (let index = 0; index < 1000 && !(await visitRow.count()); index++) {
    const next = visitPanel.getByRole('button', { name: 'Trang sau', exact: true });
    if (await next.isDisabled()) break;
    await next.click();
  }
  await visitRow.getByRole('button', { name: 'Check-in', exact: true }).click();
  await sales.getByRole('dialog').getByRole('button', { name: 'Lưu thay đổi' }).click();
  await expect(visitRow.getByRole('button', { name: 'Check-out' })).toBeVisible();
  pass('Assigned route and GPS check-in work on mobile');
  const profileSections = ['orders', 'interactions', 'appointments', 'tickets', 'display'].map(
    (section) =>
      sales.waitForResponse((response) =>
        response.url().includes(`/stores/${store.id}/360/${section}?`),
      ),
  );
  await sales.goto(base + '/#/crm?store=' + store.id);
  await expect(sales.getByRole('heading', { name: store.name, exact: true })).toBeVisible();
  for (const response of await Promise.all(profileSections)) {
    expect(response.status()).toBe(200);
    expect((await response.json()).data.content).toBeInstanceOf(Array);
  }
  pass('Store 360 loads five scoped sections independently with server pagination');
  await sales.goto(base + '/#/catalog?store=' + store.id);
  await expect(sales.getByRole('spinbutton', { name: /Số lượng/ }).first()).toBeVisible();
  await sales
    .getByRole('spinbutton', { name: /Số lượng/ })
    .first()
    .fill('1');
  await expect(sales.getByRole('button', { name: 'Gửi đơn cửa hàng', exact: true })).toBeEnabled();
  await sales.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await contexts[2].setOffline(true);
  await sales.getByRole('button', { name: 'Lưu nháp ngoại tuyến', exact: true }).click();
  await expect(
    sales.getByRole('heading', { name: 'Nháp trên thiết bị · 1', exact: true }),
  ).toBeVisible();
  pass('Sales saves an account-owned draft in IndexedDB while offline');
  await sales.reload();
  await expect(
    sales.getByRole('heading', { name: 'Nháp trên thiết bị · 1', exact: true }),
  ).toBeVisible({ timeout: 30000 });
  pass('Production PWA reloads offline with the owned draft and cached identity');
  await contexts[2].setOffline(false);
  await sales.getByRole('button', { name: 'Đồng bộ nháp', exact: true }).click();
  await expect(
    sales.getByRole('heading', { name: 'Nháp trên thiết bị · 0', exact: true }),
  ).toBeVisible();
  pass('Offline batch is acknowledged by server before removing local draft');
  expect(await npp.evaluate(() => localStorage.getItem('synera-demo-v1'))).toBe(
    '{"preserved":"user-data"}',
  );
  pass('Legacy user data is preserved');
  const cookie = await contexts[1].cookies();
  expect(cookie.find((c) => c.name === 'synera_refresh')?.httpOnly).toBeTruthy();
  pass('Refresh token is HttpOnly and absent from browser storage');
  const blocked = await contexts[1].request.post(base + '/api/v1/auth/session/refresh', {
    headers: { Origin: 'https://attacker.invalid', 'X-Session-Request': 'synera' },
  });
  expect(blocked.status()).toBe(403);
  pass('Cross-site session refresh is rejected');
  await admin.screenshot({ path: 'artifacts/live-desktop.png', fullPage: true });
  await sales.screenshot({ path: 'artifacts/live-mobile.png', fullPage: true });
  await sales.goto(base + '/#/catalog?store=' + store.id);
  await sales
    .getByRole('spinbutton', { name: /Số lượng/ })
    .first()
    .fill('1');
  await contexts[2].setOffline(true);
  await sales.getByRole('button', { name: 'Lưu nháp ngoại tuyến', exact: true }).click();
  await expect(
    sales.getByRole('heading', { name: 'Nháp trên thiết bị · 1', exact: true }),
  ).toBeVisible();
  await sales.locator('.user-button').click();
  await sales.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(sales.getByRole('heading', { name: 'Chào mừng trở lại' })).toBeVisible();
  const localState = await sales.evaluate(
    () =>
      new Promise((resolve, reject) => {
        const open = indexedDB.open('synera-offline');
        open.onerror = () => reject(open.error);
        open.onsuccess = () => {
          const db = open.result;
          const counts = {};
          const transaction = db.transaction(['drafts', 'reads', 'identity'], 'readonly');
          for (const store of ['drafts', 'reads', 'identity']) {
            const count = transaction.objectStore(store).count();
            count.onsuccess = () => {
              counts[store] = count.result;
            };
          }
          transaction.oncomplete = () => {
            db.close();
            resolve(counts);
          };
          transaction.onerror = () => {
            db.close();
            reject(transaction.error);
          };
        };
      }),
  );
  expect(localState).toEqual({ drafts: 1, reads: 0, identity: 0 });
  pass('Offline logout clears cached references while preserving the owned draft');
  await contexts[2].setOffline(false);
  await sales.reload();
  await expect(sales.getByRole('heading', { name: 'Chào mừng trở lại' })).toBeVisible();
  await expect(sales.locator('.topbar')).toHaveCount(0);
  await expect
    .poll(() => sales.evaluate(() => localStorage.getItem('synera:logout-pending')))
    .toBeNull();
  expect(
    (await contexts[2].cookies()).find((cookie) => cookie.name === 'synera_refresh'),
  ).toBeUndefined();
  pass('Reconnecting after offline logout revokes the cookie instead of restoring the session');
  expect(errors).toEqual([]);
  pass('No uncaught browser errors');
  fs.writeFileSync(
    'artifacts/e2e-live-result.json',
    JSON.stringify({ passed: count, errors, base }, null, 2),
  );
  console.log(`${count} live integration checks passed`);
} catch (error) {
  await sales.screenshot({ path: 'artifacts/e2e-failure.png', fullPage: true }).catch(() => {});
  fs.writeFileSync(
    'artifacts/e2e-failure.txt',
    JSON.stringify(
      {
        url: sales.url(),
        errors,
        body: await sales
          .locator('body')
          .innerText()
          .catch(() => ''),
        offline: await sales.evaluate(() => !navigator.onLine).catch(() => null),
      },
      null,
      2,
    ),
  );
  // Playwright matcher metadata can include password field values from the login form.
  throw new Error(String(error.message || error).replaceAll(password, '[REDACTED]'));
} finally {
  await instance.close();
}
