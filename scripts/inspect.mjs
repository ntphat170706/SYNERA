import { chromium } from '@playwright/test';
import fs from 'node:fs';
fs.mkdirSync('artifacts', { recursive: true });
const browser = await chromium.launch({
  executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  headless: true,
});
const page = await browser.newPage({
  viewport: { width: 1440, height: 1050 },
  deviceScaleFactor: 1,
});
const errors = [];
page.on('pageerror', (e) => errors.push(String(e)));
await page.goto('http://127.0.0.1:5173/#/dashboard');
await page.getByRole('heading', { name: 'Tổng quan điều hành' }).waitFor();
await page.screenshot({ path: 'artifacts/dashboard-desktop.png', fullPage: true });
console.log(
  JSON.stringify({
    title: await page.title(),
    overflow: await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
    errors,
  }),
);
await page.setViewportSize({ width: 390, height: 844 });
await page.screenshot({ path: 'artifacts/dashboard-mobile.png', fullPage: true });
console.log(
  JSON.stringify({
    mobileOverflow: await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
    errors,
  }),
);
await browser.close();
