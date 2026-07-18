import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const scannerSourceUrl = new URL('../../main/assets/shared/lookup-scanner.js', import.meta.url);

function loadScanner() {
  const window = {};
  vm.runInNewContext(fs.readFileSync(scannerSourceUrl, 'utf8'), { window });
  return window.ChimahonLookupScanner;
}

test('French scan starts at the beginning of the tapped word', () => {
  const scanner = loadScanner();
  assert.deepEqual(
    { ...scanner.scan('jamais', 4, 'fr', { scanAcrossSpaces: true }) },
    { text: 'jamais', startOffset: 0, endOffset: 6, tapOffset: 4 },
  );
});

test('French scan starts after recognized elision apostrophes', () => {
  const scanner = loadScanner();
  assert.equal(scanner.scan("l'homme", 3, 'fr').text, 'homme');
  assert.equal(scanner.scan('l’homme', 3, 'fr').text, 'homme');
  assert.equal(scanner.scan("Qu'il", 3, 'fr').text, 'il');
});

test('French scan keeps clitic and non-elision apostrophes when appropriate', () => {
  const scanner = loadScanner();
  assert.equal(scanner.scan("l'homme", 0, 'fr').text, "l'homme");
  assert.equal(scanner.scan("aujourd'hui", 9, 'fr').text, "aujourd'hui");
});

test('French scan crosses plain spaces for multi-word entries', () => {
  const scanner = loadScanner();
  assert.equal(
    scanner.scan('coup de main.', 2, 'fr', { scanAcrossSpaces: true, maxCodePoints: 80 }).text,
    'coup de main',
  );
});

test('scanner rejects punctuation and preserves forward-only non-French scans', () => {
  const scanner = loadScanner();
  assert.equal(scanner.scan('mot.', 3, 'fr'), null);
  assert.equal(scanner.scan('reading', 3, 'en').text, 'ding');
});
