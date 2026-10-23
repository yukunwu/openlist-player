import { spawn, execFileSync } from 'node:child_process';
import { readFile, mkdir, rm } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

// Set PLAYWRIGHT_MODULE to a local playwright/playwright-core module if necessary.
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const work = resolve(root, 'work/browser-test');
await mkdir(work, { recursive: true });
const java = process.env.JAVA_HOME ? `${process.env.JAVA_HOME}/bin/` : '';
execFileSync(`${java}javac`, ['-d', work,
  `${root}/app/src/main/java/site/openlist/player/PairingProtocol.java`,
  `${root}/app/src/main/java/site/openlist/player/PairingServer.java`,
  `${root}/scripts/PairingBrowserHarness.java`]);
const payloadFile = `${work}/synthetic-payload.json`;
await rm(payloadFile, { force: true });
const harness = spawn(`${java}java`, ['-cp', work, 'site.openlist.player.PairingBrowserHarness',
  `${root}/app/src/main/assets`, payloadFile], { stdio: ['ignore', 'pipe', 'pipe'] });
let browser;
try {
  const url = await new Promise((accept, reject) => {
    let value = '';
    const timer = setTimeout(() => reject(new Error('Harness startup timed out')), 10000);
    harness.stdout.on('data', b => {
      value += b;
      if (value.includes('\n')) { clearTimeout(timer); accept(value.split('\n')[0].trim()); }
    });
    harness.once('error', reject);
    harness.once('exit', code => { if (!value) { clearTimeout(timer); reject(new Error(`Harness exited ${code}`)); } });
  });
  browser = await chromium.launch({ headless: true,
    ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  const locale = process.env.TEST_LOCALE || 'en-US';
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, locale });
  const requests = [];
  page.on('request', r => requests.push({ url: r.url(), body: r.postData() }));
  await page.goto(url);
  const chinese = locale.startsWith('zh');
  assert.equal(await page.locator('h1').textContent(), chinese ? '把片库带到电视' : 'Your library, on TV');
  assert.equal(await page.locator('#server').inputValue(), '');
  assert.equal(await page.locator('#username').inputValue(), '');
  assert.equal(await page.evaluate(() => typeof forge.pki), 'undefined', 'RSA/PKI must not be bundled');
  assert.equal(await page.evaluate(() => typeof forge.asn1), 'undefined', 'ASN.1 must not be bundled');
  assert.equal(await page.evaluate(() => getComputedStyle(document.documentElement).backgroundColor), 'rgb(16, 16, 16)');
  assert.equal(new URL(page.url()).hash, '', 'Fragment key must be removed from browser history');
  await page.locator('#server').fill('https://media.example.test');
  await page.locator('#username').fill('只读测试');
  await page.locator('#password').fill('synthetic-test-only-123');
  await page.locator('#otp').fill('123456');
  await page.locator('#send').click();
  await page.waitForFunction(() => document.querySelector('#form').hidden);
  assert.equal(await page.locator('#status').textContent(), chinese ? '已发送，请回到电视确认。' : 'Sent. Confirm on your TV.');
  const payload = JSON.parse(await readFile(payloadFile, 'utf8'));
  assert.deepEqual(payload, { server: 'https://media.example.test', username: '只读测试',
    password: 'synthetic-test-only-123', otp: '123456' });
  const post = requests.find(x => x.body);
  assert(post && /^[0-9a-f]+$/.test(post.body));
  assert(!post.body.includes('synthetic-test-only-123'));
  assert(requests.every(x => new URL(x.url).host === new URL(url).host));
  assert.equal(await page.locator('#password').inputValue(), '');
  await page.screenshot({ path: `${work}/sent-${locale}.png`, fullPage: true });
  console.log('PASS: mobile browser form -> AES-GCM -> Java receiver; no external requests or plaintext credential on wire');
} finally {
  await browser?.close(); harness.kill('SIGTERM');
}
