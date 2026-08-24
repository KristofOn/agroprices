'use strict';

/**
 * The SEUROP grid splits each conformation class into fatness sub-classes
 * (e.g. "DU2" and "DU3" = koeien, klasse U, vetheid 2 resp. 3). The widget
 * shows one price per conformation class (DU, DE), so we average the
 * available fatness sub-classes for that class. This is a deliberate,
 * documented simplification -- adjust CATEGORY_GROUPS below if a single
 * specific fatness class is preferred instead.
 */
const CATEGORY_GROUPS = {
  DU: ['DU2', 'DU3'],
  DE: ['DE2', 'DE3'],
};

function average(values) {
  const present = values.filter((v) => typeof v === 'number' && Number.isFinite(v));
  if (present.length === 0) return null;
  const mean = present.reduce((sum, v) => sum + v, 0) / present.length;
  return Math.round(mean * 100) / 100;
}

function groupPrice(row, columns) {
  if (!row) return null;
  return average(columns.map((col) => row.categories[col]));
}

/**
 * @param {ReturnType<import('./parseBeefCsv').parseBeefCsv>} rows sorted ascending by (year, week)
 * @param {string} generatedAtIso
 */
function computeBeefSummary(rows, generatedAtIso) {
  if (rows.length === 0) {
    throw new Error('Geen rijen om samen te vatten');
  }

  const latest = rows[rows.length - 1];
  const previous = rows.length >= 2 ? rows[rows.length - 2] : null;

  const summary = {
    generatedAt: generatedAtIso,
    weekLabel: `week ${latest.week} ${latest.year}`,
    previousWeekLabel: previous ? `week ${previous.week} ${previous.year}` : null,
  };

  for (const [label, columns] of Object.entries(CATEGORY_GROUPS)) {
    summary[label] = {
      price: groupPrice(latest, columns),
      previousPrice: groupPrice(previous, columns),
    };
  }

  return summary;
}

module.exports = { computeBeefSummary, CATEGORY_GROUPS };
