import { chromium, expect as baseExpect } from '@playwright/test';
import fs from 'node:fs';
import { login, password } from './browser-session.mjs';
const base = process.env.TEST_URL || 'http://127.0.0.1:8080';
const expect = baseExpect.configure({
  timeout: Number(process.env.TEST_EXPECT_TIMEOUT_MS || 5000),
});
const browser = await chromium.launch({
  channel: process.env.BROWSER_CHANNEL || 'msedge',
  headless: true,
});
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
page.setDefaultTimeout(Number(process.env.TEST_EXPECT_TIMEOUT_MS || 30000));
const errors = [];
const apiErrors = [];
let signingOut = false;
page.on('pageerror', (e) => errors.push(String(e)));
page.on('response', (response) => {
  if (
    response.url().includes('/api/v1/') &&
    response.status() >= 400 &&
    !response.url().includes('/auth/session/refresh') &&
    !(signingOut && response.status() === 401)
  )
    apiErrors.push(`${response.status()} ${new URL(response.url()).pathname}`);
});
let count = 0;
const pass = (name) => {
  console.log('PASS ' + name);
  count++;
};
const go = async (path) => {
  await page.goto(base + '/#' + path);
  await expect(page.locator('.loading')).toHaveCount(0, { timeout: 30000 });
};
fs.mkdirSync('artifacts', { recursive: true });
try {
  await go('/landing');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Một nền tảng');
  await expect(page.locator('.landing-header')).not.toHaveClass(/scrolled/);
  await page.screenshot({
    path: 'artifacts/landing-desktop.png',
    fullPage: true,
    style: '.landing-reveal{opacity:1!important;transform:none!important;}',
  });
  await page.evaluate(() => scrollTo(0, 400));
  await expect(page.locator('.landing-header')).toHaveClass(/scrolled/);
  await page.locator('.solutions-menu summary').click();
  await expect(page.locator('.solutions-mega')).toBeVisible();
  expect(
    await page
      .locator('.solutions-mega')
      .evaluate((el) => getComputedStyle(el).gridTemplateColumns.split(' ').length),
  ).toBe(3);
  pass('Landing uses supplied video, sticky header and three-column solution menu');
  await login(page, 'admin@synera.local');
  await go('/dashboard');
  expect(
    await page.locator('.sidebar').evaluate((el) => getComputedStyle(el).backgroundColor),
  ).toBe('rgb(31, 89, 150)');
  expect(await page.locator('.sidebar').evaluate((el) => el.getBoundingClientRect().width)).toBe(
    76,
  );
  await expect(page.locator('.nsx-primary-nav .nav-link')).toHaveCount(6);
  await page.reload();
  await expect(page.locator('.loading')).toHaveCount(0, { timeout: 30000 });
  expect(await page.locator('.sidebar').evaluate((el) => el.getBoundingClientRect().width)).toBe(
    76,
  );
  await page.screenshot({ path: 'artifacts/dashboard-desktop.png', fullPage: true });
  pass('Manufacturer uses the requested 76px blue rail and six operational areas');
  await go('/partners');
  await page.locator('thead th').first().getByRole('button').click();
  await expect(page.locator('th').first()).toHaveAttribute('aria-sort', 'ascending');
  await page.locator('thead th').first().getByRole('button').click();
  await expect(page.locator('th').first()).toHaveAttribute('aria-sort', 'descending');
  pass('Shared tables sort without mutating data');
  await page.getByRole('button', { name: 'Tìm kiếm nhanh...' }).click();
  await page.getByLabel('Mã đơn, sản phẩm, cửa hàng, nhà phân phối...').fill('SKU-003');
  await page.locator('.search-result').filter({ hasText: 'Nước khoáng' }).click();
  await expect(page).toHaveURL(/catalog\?q=/);
  await expect(page.locator('.product-card')).toHaveCount(1);
  await expect(page.locator('.product-card select option')).toHaveCount(3);
  pass('Global SKU search opens one product with its three selectable units');
  await go('/orders');
  await page.getByRole('button', { name: /^Chờ xử lý/ }).click();
  await expect(page).toHaveURL(/status=pending/);
  await page.reload();
  await expect(page.locator('.loading')).toHaveCount(0, { timeout: 30000 });
  await expect(page.locator('.tabs button.active').filter({ hasText: 'Chờ xử lý' })).toHaveCount(1);
  pass('Order queue filter survives refresh through URL');
  await page.setViewportSize({ width: 900, height: 900 });
  expect(await page.locator('.sidebar').evaluate((el) => el.getBoundingClientRect().width)).toBe(
    76,
  );
  pass('Tablet uses compact navigation rail');
  await page.setViewportSize({ width: 390, height: 844 });
  await go('/dashboard');
  await page.getByRole('button', { name: 'Mở menu', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Menu quản trị' })).toBeVisible();
  expect(
    await page.evaluate(() => document.querySelector('.sidebar').contains(document.activeElement)),
  ).toBe(true);
  expect(await page.evaluate(() => document.body.style.overflow)).toBe('hidden');
  await page.keyboard.press('Escape');
  await expect(page.locator('.sidebar.open')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Mở menu', exact: true })).toBeFocused();
  pass('Mobile drawer locks background, handles Escape and restores focus');
  await go('/orders');
  expect(
    await page
      .locator('.responsive-table tr')
      .first()
      .evaluate((el) => getComputedStyle(el.parentElement).display),
  ).toBe('none');
  await page.screenshot({ path: 'artifacts/orders-mobile.png', fullPage: true });
  for (const path of [
    '/dashboard',
    '/orders',
    '/partners',
    '/finance',
    '/inventory',
    '/reports',
    '/policies',
    '/tickets',
    '/settings',
    '/delivery',
    '/demand',
    '/trade',
    '/engagement',
    '/display',
    '/references',
  ]) {
    await go(path);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      path,
    ).toBe(true);
  }
  await go('/dashboard');
  await page.screenshot({ path: 'artifacts/dashboard-mobile.png', fullPage: true });
  pass('Mobile admin uses card tables and no page overflows');
  await go('/landing');
  await page.screenshot({
    path: 'artifacts/landing-mobile.png',
    fullPage: true,
    style: '.landing-reveal{opacity:1!important;transform:none!important;}',
  });
  await page.getByRole('button', { name: 'Mở menu website' }).click();
  await page
    .getByRole('navigation', { name: 'Điều hướng website mobile' })
    .getByRole('link', { name: 'Giải pháp', exact: true })
    .click();
  await expect(page.locator('#landing-mobile-menu')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  pass('Landing mobile menu and anchor navigation work');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await go('/landing');
  await expect(page.locator('.landing-hero video')).toBeHidden();
  pass('Reduced motion disables background video and reveal animations');
  await page.setViewportSize({ width: 1440, height: 1000 });
  await go('/dashboard');
  await page.getByRole('button', { name: /Quản trị SYNERA/ }).click();
  signingOut = true;
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await go('/login');
  await expect(page.getByLabel('Email', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Vai trò trải nghiệm')).toHaveCount(0);
  await page.screenshot({ path: 'artifacts/login-desktop.png', fullPage: true });
  pass('Login uses server identity and exposes no demo role switch');
  expect(errors).toEqual([]);
  expect(apiErrors).toEqual([]);
  pass('No uncaught JavaScript errors during design checks');
  fs.writeFileSync(
    'artifacts/design-results.json',
    JSON.stringify({ passed: count, errors, date: new Date().toISOString() }, null, 2),
  );
} catch (error) {
  await page.screenshot({ path: 'artifacts/design-failure.png', fullPage: true }).catch(() => {});
  fs.writeFileSync(
    'artifacts/design-failure.json',
    JSON.stringify(
      {
        url: page.url(),
        apiErrors,
        errors,
        overflow: await page
          .evaluate(() =>
            [...document.querySelectorAll('body *')]
              .filter(
                (el) =>
                  el.getBoundingClientRect().right > innerWidth &&
                  getComputedStyle(el).display !== 'none',
              )
              .map((el) => ({
                tag: el.tagName,
                class: el.className,
                width: Math.round(el.getBoundingClientRect().width),
                text: el.textContent?.slice(0, 90),
              }))
              .slice(0, 30),
          )
          .catch(() => []),
      },
      null,
      2,
    ),
  );
  throw new Error(String(error.message || error).replaceAll(password, '[REDACTED]'));
} finally {
  await browser.close();
}
