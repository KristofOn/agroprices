'use strict';

/**
 * Parses the CSV export from the "Download data" button on the koeien
 * (cows) carcass-price chart at landbouwcijfers.vlaanderen.be.
 *
 * Format (observed live, 24/08/2026):
 *   "Jaar";"week";"DE2";"DE3";"DO2";"DO3";"DO4";"DP2";"DP3";"DR2";"DR3";"DS2";"DS3";"DU2";"DU3";
 *   "2011";"1";"420.24";"368.37";...
 *   ...
 *   Bron: verschillende slachthuizen en handelaren   <- trailing footer line, not data
 *
 * Plain UTF-8/ASCII text, semicolon-separated, quoted fields, dot as decimal
 * separator (unlike fegra.be, which uses a comma). Category columns are not
 * fixed in count or order across all periods in principle, so this parser
 * matches by header name rather than a hardcoded column index.
 *
 * @param {string} csvText
 * @returns {{year: number, week: number, categories: Record<string, number|null>}[]}
 */
function parseBeefCsv(csvText) {
  const lines = csvText
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0);

  if (lines.length === 0) {
    throw new Error('Leeg CSV-bestand');
  }

  const splitRow = (line) =>
    line
      .split(';')
      .map((field) => field.trim().replace(/^"|"$/g, ''));

  const header = splitRow(lines[0]);
  const yearIdx = header.findIndex((h) => h.toLowerCase() === 'jaar');
  const weekIdx = header.findIndex((h) => h.toLowerCase() === 'week');
  if (yearIdx === -1 || weekIdx === -1) {
    throw new Error('Onverwachte CSV-header: Jaar/week kolommen niet gevonden: ' + JSON.stringify(header));
  }

  const categoryIndices = header
    .map((name, idx) => ({ name, idx }))
    .filter(({ idx }) => idx !== yearIdx && idx !== weekIdx && header[idx] !== '');

  const rows = [];
  for (let i = 1; i < lines.length; i++) {
    const fields = splitRow(lines[i]);
    const yearRaw = fields[yearIdx];
    const weekRaw = fields[weekIdx];
    const year = Number(yearRaw);
    const week = Number(weekRaw);
    if (!Number.isInteger(year) || !Number.isInteger(week)) {
      // Skips the trailing "Bron: ..." footer line and any other non-data row.
      continue;
    }

    const categories = {};
    for (const { name, idx } of categoryIndices) {
      const raw = fields[idx];
      categories[name] = raw === undefined || raw === '' ? null : Number(raw);
    }

    rows.push({ year, week, categories });
  }

  rows.sort((a, b) => (a.year - b.year) || (a.week - b.week));
  return rows;
}

module.exports = { parseBeefCsv };
