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
// The div the Spotfire iframe actually gets injected into uses the same id
// minus the "doc_" prefix (confirmed in the rendered page HTML).
const KOEIEN_DIV_ID = 'hestocom';

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
  // Diagnostics only: captures what the page actually looked like if a run
  // fails, instead of guessing blind. Harmless on the success path --
  // nothing is written unless the run fails, see the catch block below.
  const debugEvents = [];
  try {
    const context = await browser.newContext({
      viewport: { width: 1400, height: 1400 },
      acceptDownloads: true,
    });
    const page = await context.newPage();
    page.on('console', (msg) => debugEvents.push(`[console.${msg.type()}] ${msg.text()}`));
    page.on('requestfailed', (req) =>
      debugEvents.push(`[requestfailed] ${req.url()} -- ${req.failure()?.errorText}`)
    );
    page.on('response', (res) => {
      if (res.status() >= 400) debugEvents.push(`[response ${res.status()}] ${res.url()}`);
    });

    await page.goto(PAGE_URL, { waitUntil: 'domcontentloaded', timeout: 60000 });

    // The cookie-consent dialog overlays the download buttons and blocks
    // clicks until dismissed. Reject non-essential cookies (least data
    // shared) rather than accepting -- this is a public data page and no
    // cookie-dependent feature is needed here. Match on "weiger" alone
    // (not "weiger alle") since the Cookiebot banner's actual label is
    // just "Weigeren".
    const rejectCookiesBtn = page.getByRole('button', { name: /weiger/i });
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

    // The button exists in the DOM (as a dumb "#hestocom" placeholder div)
    // as soon as the page shell loads, well before the embedded Spotfire
    // document (a cross-origin iframe injected by the page's own JS) has
    // actually loaded into it. Clicking before that iframe exists produces
    // no download at all -- confirmed via a CI failure where the debug
    // screenshot/HTML showed this exact chart's container still empty
    // after a blind fixed wait, while sibling charts above it on the page
    // had already loaded fine. Wait for the real readiness signal (the
    // iframe's presence) instead of guessing a delay.
    await page.locator(`#${KOEIEN_DIV_ID} iframe`).waitFor({ state: 'attached', timeout: 90000 });
    // The iframe tag appearing doesn't mean Spotfire has finished rendering
    // inside it yet -- give it a short settle buffer before clicking.
    await page.waitForTimeout(5000);

    // Loading several Spotfire documents on one page appears to be flaky
    // under load (this is the third of four), so retry the click a few
    // times before giving up, since the button itself stays clickable
    // throughout.
    let download;
    let lastErr;
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      try {
        [download] = await Promise.all([
          page.waitForEvent('download', { timeout: 30000 }),
          downloadBtn.click(),
        ]);
        lastErr = null;
        break;
      } catch (err) {
        lastErr = err;
      }
    }
    if (lastErr) {
      const debugDir = process.env.BEEF_DEBUG_DIR || os.tmpdir();
      fs.mkdirSync(debugDir, { recursive: true });
      const stamp = Date.now();
      const screenshotPath = path.join(debugDir, `agroprices-debug-${stamp}.png`);
      const htmlPath = path.join(debugDir, `agroprices-debug-${stamp}.html`);
      const eventsPath = path.join(debugDir, `agroprices-debug-${stamp}.log`);
      await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {});
      await page
        .content()
        .then((html) => fs.writeFileSync(htmlPath, html, 'utf-8'))
        .catch(() => {});
      fs.writeFileSync(eventsPath, debugEvents.join('\n'), 'utf-8');
      lastErr.message += ` (debug output: ${screenshotPath}, ${htmlPath}, ${eventsPath})`;
      throw lastErr;
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
