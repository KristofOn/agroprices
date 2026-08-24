'use strict';

const fs = require('fs');
const path = require('path');

const { downloadBeefCsv } = require('./lib/downloadBeefCsv');
const { parseBeefCsv } = require('./lib/parseBeefCsv');
const { computeBeefSummary } = require('./lib/computeBeefSummary');
const { validateBeefSummary } = require('./lib/validateBeefSummary');

const OUTPUT_PATH = path.join(__dirname, '..', 'docs', 'beef.json');

async function main() {
  console.log('Runderkarkasprijzen (koeien DU/DE) scraper gestart:', new Date().toISOString());

  let csvText;
  try {
    csvText = await downloadBeefCsv();
  } catch (err) {
    console.error('FOUT tijdens downloaden van de Spotfire-data:', err.message);
    console.error(err.stack);
    process.exitCode = 1;
    return;
  }
  console.log(`CSV gedownload (${csvText.length} tekens).`);

  let rows;
  try {
    rows = parseBeefCsv(csvText);
  } catch (err) {
    console.error('FOUT tijdens parsen van de CSV:', err.message);
    process.exitCode = 1;
    return;
  }
  console.log(`${rows.length} weekrijen geparsed.`);

  if (rows.length === 0) {
    console.error('FOUT: geen rijen na parsen -- CSV-formaat is vermoedelijk gewijzigd.');
    process.exitCode = 1;
    return;
  }

  const summary = computeBeefSummary(rows, new Date().toISOString());
  console.log('Samenvatting:', JSON.stringify(summary, null, 2));

  const { ok, errors, warnings } = validateBeefSummary(summary);
  for (const w of warnings) console.warn('WAARSCHUWING:', w);

  if (!ok) {
    for (const e of errors) console.error('VALIDATIEFOUT:', e);
    console.error(
      `Validatie mislukt -- ${OUTPUT_PATH} wordt NIET overschreven. De vorige (mogelijk iets oudere) publicatie blijft staan.`
    );
    process.exitCode = 1;
    return;
  }

  fs.mkdirSync(path.dirname(OUTPUT_PATH), { recursive: true });
  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(summary, null, 2) + '\n', 'utf-8');
  console.log('Geschreven naar', OUTPUT_PATH);
}

main().catch((err) => {
  console.error('Onverwachte fout:', err);
  process.exitCode = 1;
});
