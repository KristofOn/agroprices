'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { parseBeefCsv } = require('../lib/parseBeefCsv');
const { computeBeefSummary, CATEGORY_GROUPS } = require('../lib/computeBeefSummary');
const { validateBeefSummary } = require('../lib/validateBeefSummary');

const SAMPLE_CSV = [
  '"Jaar";"week";"DE2";"DE3";"DO2";"DO3";"DO4";"DP2";"DP3";"DR2";"DR3";"DS2";"DS3";"DU2";"DU3";',
  '"2026";"32";"872.58";"839.07";"480.03";"496.73";"539.83";"418.35";"448.76";"611.57";"597.20";"914.67";"881.03";"738.83";"695.52";',
  '"2026";"33";"876.57";"837.45";"489.13";"500.47";"541.95";"419.60";"450.02";"609.19";"599.64";"918.90";"887.10";"746.91";"682.77";',
  'Bron: verschillende slachthuizen en handelaren',
].join('\n');

test('parseBeefCsv: matches header names to columns and skips the footer line', () => {
  const rows = parseBeefCsv(SAMPLE_CSV);
  assert.equal(rows.length, 2);
  assert.deepEqual(
    rows.map((r) => [r.year, r.week]),
    [[2026, 32], [2026, 33]]
  );
  assert.equal(rows[1].categories.DU2, 746.91);
  assert.equal(rows[1].categories.DU3, 682.77);
  assert.equal(rows[1].categories.DE2, 876.57);
});

test('parseBeefCsv: sorts rows ascending by year then week even if input is out of order', () => {
  const shuffled = [
    '"Jaar";"week";"DU2";"DU3";',
    '"2026";"33";"1";"1";',
    '"2025";"52";"2";"2";',
    '"2026";"1";"3";"3";',
  ].join('\n');
  const rows = parseBeefCsv(shuffled);
  assert.deepEqual(
    rows.map((r) => [r.year, r.week]),
    [[2025, 52], [2026, 1], [2026, 33]]
  );
});

test('parseBeefCsv: empty fields become null, not NaN or 0', () => {
  const withGaps = [
    '"Jaar";"week";"DU2";"DU3";',
    '"2026";"33";"";"682.77";',
  ].join('\n');
  const rows = parseBeefCsv(withGaps);
  assert.equal(rows[0].categories.DU2, null);
  assert.equal(rows[0].categories.DU3, 682.77);
});

test('computeBeefSummary: averages the fatness sub-classes per conformation class', () => {
  const rows = parseBeefCsv(SAMPLE_CSV);
  const summary = computeBeefSummary(rows, '2026-08-24T10:00:00.000Z');

  assert.equal(summary.weekLabel, 'week 33 2026');
  assert.equal(summary.previousWeekLabel, 'week 32 2026');

  const expectedDU = Math.round(((746.91 + 682.77) / 2) * 100) / 100;
  const expectedDE = Math.round(((876.57 + 837.45) / 2) * 100) / 100;
  assert.equal(summary.DU.price, expectedDU);
  assert.equal(summary.DE.price, expectedDE);

  const expectedPrevDU = Math.round(((738.83 + 695.52) / 2) * 100) / 100;
  assert.equal(summary.DU.previousPrice, expectedPrevDU);
});

test('computeBeefSummary: falls back to a single sub-class if the other is missing', () => {
  const csv = [
    '"Jaar";"week";"DU2";"DU3";',
    '"2026";"33";"";"682.77";',
  ].join('\n');
  const rows = parseBeefCsv(csv);
  const summary = computeBeefSummary(rows, '2026-08-24T10:00:00.000Z');
  assert.equal(summary.DU.price, 682.77);
});

test('computeBeefSummary: single-row input has no previousPrice', () => {
  const csv = [
    '"Jaar";"week";"DU2";"DU3";',
    '"2026";"33";"700";"680";',
  ].join('\n');
  const rows = parseBeefCsv(csv);
  const summary = computeBeefSummary(rows, '2026-08-24T10:00:00.000Z');
  assert.equal(summary.previousWeekLabel, null);
  assert.equal(summary.DU.previousPrice, null);
});

test('CATEGORY_GROUPS covers exactly DU and DE', () => {
  assert.deepEqual(Object.keys(CATEGORY_GROUPS).sort(), ['DE', 'DU']);
});

test('validateBeefSummary: accepts a plausible summary', () => {
  const summary = {
    generatedAt: new Date().toISOString(),
    weekLabel: 'week 33 2026',
    DU: { price: 714.84, previousPrice: 717.18 },
    DE: { price: 857.01, previousPrice: 855.83 },
  };
  const { ok, errors } = validateBeefSummary(summary);
  assert.equal(ok, true, JSON.stringify(errors));
});

test('validateBeefSummary: rejects a missing price', () => {
  const summary = {
    generatedAt: new Date().toISOString(),
    weekLabel: 'week 33 2026',
    DU: { price: null, previousPrice: 717.18 },
    DE: { price: 857.01, previousPrice: 855.83 },
  };
  const { ok, errors } = validateBeefSummary(summary);
  assert.equal(ok, false);
  assert.ok(errors.some((e) => e.includes('DU')));
});

test('validateBeefSummary: rejects an implausibly low or high price', () => {
  const tooLow = validateBeefSummary({
    generatedAt: new Date().toISOString(),
    weekLabel: 'week 33 2026',
    DU: { price: 5, previousPrice: 717.18 },
    DE: { price: 857.01, previousPrice: 855.83 },
  });
  assert.equal(tooLow.ok, false);

  const tooHigh = validateBeefSummary({
    generatedAt: new Date().toISOString(),
    weekLabel: 'week 33 2026',
    DU: { price: 714.84, previousPrice: 717.18 },
    DE: { price: 99999, previousPrice: 855.83 },
  });
  assert.equal(tooHigh.ok, false);
});

test('validateBeefSummary: rejects a missing/invalid generatedAt', () => {
  const { ok, errors } = validateBeefSummary({
    generatedAt: 'not-a-date',
    weekLabel: 'week 33 2026',
    DU: { price: 714.84, previousPrice: 717.18 },
    DE: { price: 857.01, previousPrice: 855.83 },
  });
  assert.equal(ok, false);
  assert.ok(errors.some((e) => e.includes('generatedAt')));
});
