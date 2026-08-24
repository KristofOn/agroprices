'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');

const PAGE_URL = 'https://landbouwcijfers.vlaanderen.be/marktinformatie/rundvee/prijzen-van-runderkarkassen';

// "doc_hestocom" is the Spotfire document id for the koeien (cows) carcass
// price chart on this page (confirmed via its document metadata: path
// "/PubliekInternet/Indicatoren/DVIZ - Prijzen karkassen koeien"). The page
// embeds 4 separate charts (algemeen/stieren/koeien/vaarzen); this id is
// what scopes the "Download data" click to the koeien one specifically.
// If Vlaanderen ever rebuilds this page, this id is the first thing to
// re-verify (inspect data-spotfire-container-id on the koeien block's
// "Download data" link).
const KOEIEN_CONTAINER_ID = 'doc_hestocom';

/**
 * Drives a headless browser to the public Vlaanderen dashboard and clicks
 * the "Download data" button for the koeien (cows) carcass-price chart,
 * returning the raw CSV text it produces.
 *
 * This deliberately uses the same UI export path a human visitor would use
 * (rather than the page's internal Spotfire JS API, which returned only
 * distinct-value counts and no actual values for anonymous visitors during
 * testing -- likely a guest-license restriction). The download button is
 * public-facing by design on this "PubliekInternet" dashboard.
 *
 * @returns {Promise<string>} raw CSV text
 */
async function downloadBeefCsv() {
  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext({
      viewport: { width: 1400, height: 1400 },
      acceptDownloads: true,
    });
    const page = await context.newPage();

    await page.goto(PAGE_URL, { waitUntil: 'domcontentloaded', timeout: 60000 });

    // The cookie-consent dialog overlays the download buttons and blocks
    // clicks until dismissed. Reject non-essential cookies (least data
    // shared) rather than accepting -- this is a public data page and no
    // cookie-dependent feature is needed here.
    const rejectCookiesBtn = page.getByRole('button', { name: /weiger alle/i });
    try {
      await rejectCookiesBtn.waitFor({ state: 'visible', timeout: 15000 });
      await rejectCookiesBtn.click();
    } catch {
      // Banner may not appear (e.g. consent already recorded for this
      // context) -- proceed either way, later waits/asserts will fail
      // loudly if the page is actually still blocked.
    }

    const downloadBtn = page.locator(
      `a[data-spotfire-container-id="${KOEIEN_CONTAINER_ID}"][data-spotfire-document-property="Download"]`
    );
    await downloadBtn.waitFor({ state: 'visible', timeout: 45000 });

    // The button exists in the DOM as soon as the page shell loads, but the
    // embedded Spotfire document (loaded in a cross-origin iframe) needs
    // extra time to finish initializing before a click actually produces a
    // download -- there is no reliable DOM signal to wait on instead, so a
    // fixed delay is used here (confirmed necessary during manual testing).
    await page.waitForTimeout(15000);

    let download;
    try {
      [download] = await Promise.all([
        page.waitForEvent('download', { timeout: 30000 }),
        downloadBtn.click(),
      ]);
    } catch (err) {
      const debugPath = path.join(os.tmpdir(), `agroprices-debug-${Date.now()}.png`);
      await page.screenshot({ path: debugPath, fullPage: true }).catch(() => {});
      err.message += ` (debug screenshot: ${debugPath})`;
      throw err;
    }

    const tmpPath = path.join(os.tmpdir(), `agroprices-beef-${Date.now()}.csv`);
    await download.saveAs(tmpPath);
    const text = fs.readFileSync(tmpPath, 'utf-8');
    fs.unlinkSync(tmpPath);

    if (!text || text.trim().length === 0) {
      throw new Error('Download leverde een leeg bestand op');
    }

    return text;
  } finally {
    await browser.close();
  }
}

module.exports = { downloadBeefCsv, KOEIEN_CONTAINER_ID, PAGE_URL };
