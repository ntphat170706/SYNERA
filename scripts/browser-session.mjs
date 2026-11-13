import { chromium, expect as baseExpect } from '@playwright/test';
export const base = process.env.TEST_URL || 'http://127.0.0.1:8080';
export const password = process.env.BOOTSTRAP_PASSWORD || 'SyneraLocal2026!';
const expect = baseExpect.configure({
  timeout: Number(process.env.TEST_EXPECT_TIMEOUT_MS || 5000),
});
export async function browser() {
  return chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'msedge', headless: true });
}
export async function login(page, username) {
  await page.goto(base + '/#/login');
  await page.getByLabel('Email', { exact: true }).fill(username);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Tiếp tục đăng nhập' }).click();
  if (username === 'admin@synera.local' || username === 'accountant@synera.local') {
    const otp = await page.locator('.info-box strong').innerText();
    await page.getByLabel('Mã OTP', { exact: true }).fill(otp);
    await page.getByRole('button', { name: 'Tiếp tục đăng nhập' }).click();
  }
  await expect(page.locator('.topbar')).toBeVisible();
}
export async function token(request, username) {
  let response = await request.post(base + '/api/v1/auth/login', { data: { username, password } });
  expect(response.ok()).toBeTruthy();
  let data = (await response.json()).data;
  if (data.requiresOtp) {
    response = await request.post(base + '/api/v1/auth/otp/verify', {
      data: { challengeId: data.challengeId, code: data.localOtp },
    });
    expect(response.ok()).toBeTruthy();
    data = (await response.json()).data;
  }
  return data.accessToken;
}
export async function command(request, access, path, data = {}) {
  const response = await request.post(base + '/api/v1' + path, {
    headers: { Authorization: 'Bearer ' + access, 'Idempotency-Key': crypto.randomUUID() },
    data,
  });
  const envelope = await response.json();
  if (!response.ok())
    throw new Error(`${path}: ${envelope.error?.code}: ${envelope.error?.message}`);
  return envelope.data;
}
