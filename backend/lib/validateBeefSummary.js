'use strict';

const MIN_PLAUSIBLE_PRICE = 100; // EUR/100kg
const MAX_PLAUSIBLE_PRICE = 2000; // EUR/100kg
const WARN_JUMP_RATIO = 0.35; // week-over-week change above this is logged, not rejected

/**
 * Plausibility gate before publishing. Deliberately conservative: this only
 * rejects data that is very likely a scraping/parsing bug (missing prices,
 * wrong CSV shape, absurd numbers), not real market volatility. On failure,
 * the caller should keep the previously published docs/beef.json untouched
 * rather than overwrite it with bad data.
 *
 * @param {ReturnType<import('./computeBeefSummary').computeBeefSummary>} summary
 * @returns {{ok: boolean, errors: string[], warnings: string[]}}
 */
function validateBeefSummary(summary) {
  const errors = [];
  const warnings = [];

  if (!summary || typeof summary !== 'object') {
    return { ok: false, errors: ['summary is geen object'], warnings };
  }

  if (!summary.generatedAt || Number.isNaN(Date.parse(summary.generatedAt))) {
    errors.push('generatedAt ontbreekt of is geen geldige datum');
  }

  if (!summary.weekLabel) {
    errors.push('weekLabel ontbreekt');
  }

  for (const label of ['DU', 'DE']) {
    const entry = summary[label];
    if (!entry) {
      errors.push(`${label}: ontbreekt volledig in de samenvatting`);
      continue;
    }
    if (entry.price === null || entry.price === undefined) {
      errors.push(`${label}: geen actuele prijs gevonden (alle onderliggende kolommen leeg)`);
      continue;
    }
    if (typeof entry.price !== 'number' || !Number.isFinite(entry.price)) {
      errors.push(`${label}: prijs is geen geldig getal (${entry.price})`);
      continue;
    }
    if (entry.price < MIN_PLAUSIBLE_PRICE || entry.price > MAX_PLAUSIBLE_PRICE) {
      errors.push(`${label}: prijs ${entry.price} valt buiten het plausibele bereik [${MIN_PLAUSIBLE_PRICE}, ${MAX_PLAUSIBLE_PRICE}]`);
    }

    if (typeof entry.previousPrice === 'number' && entry.previousPrice > 0 && typeof entry.price === 'number') {
      const change = Math.abs(entry.price - entry.previousPrice) / entry.previousPrice;
      if (change > WARN_JUMP_RATIO) {
        warnings.push(`${label}: prijs veranderde ${(change * 100).toFixed(1)}% t.o.v. vorige week (${entry.previousPrice} -> ${entry.price}) -- controleer handmatig`);
      }
    }
  }

  if (
    summary.DU && summary.DE &&
    typeof summary.DU.price === 'number' && typeof summary.DE.price === 'number' &&
    summary.DU.price === summary.DE.price
  ) {
    warnings.push('DU en DE hebben exact dezelfde prijs -- mogelijk een extractiefout (kolommen door elkaar gehaald)');
  }

  return { ok: errors.length === 0, errors, warnings };
}

module.exports = { validateBeefSummary, MIN_PLAUSIBLE_PRICE, MAX_PLAUSIBLE_PRICE };
