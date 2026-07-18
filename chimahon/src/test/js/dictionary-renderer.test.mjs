import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const rendererSourceUrl = new URL('../../main/assets/dictionary/renderer.js', import.meta.url);

class FakeElement {
  constructor(tagName = 'div') {
    this.children = [];
    this.className = '';
    this.dataset = {};
    this.tagName = tagName.toUpperCase();
    this.textContent = '';
    const classes = new Set();
    this.classList = {
      add: (...names) => names.forEach((name) => classes.add(name)),
      contains: (name) => classes.has(name),
    };
  }

  appendChild(child) {
    this.children.push(child);
    return child;
  }
}

function loadStructuredRenderer() {
  const source = fs.readFileSync(rendererSourceUrl, 'utf8').replace(
    '  function createLinkNode(',
    '  window.__rendererTest = { appendStructured, isDeinflectionGlossary };\n\n  function createLinkNode(',
  );
  const document = {
    body: new FakeElement('body'),
    documentElement: new FakeElement('html'),
    createElement: (tagName) => new FakeElement(tagName),
    createTextNode: (text) => ({ children: [], textContent: String(text) }),
  };
  const window = { addEventListener() {} };
  vm.runInNewContext(source, { console, document, window });
  return { document, renderer: window.__rendererTest };
}

function renderedText(node) {
  return (node.textContent || '') + (node.children || []).map(renderedText).join('');
}

test('renderer spaces a Yomitan deinflection lemma and rule', () => {
  const { renderer } = loadStructuredRenderer();
  const parent = new FakeElement();
  renderer.appendStructured(
    parent,
    ['détester', ['third-person singular imperfect indicative']],
    'wty-fr-en',
    {},
    'fr',
  );

  assert.equal(renderedText(parent), 'détester third-person singular imperfect indicative');
});

test('renderer puts multiple Yomitan deinflection senses in separate list items', () => {
  const { renderer } = loadStructuredRenderer();
  const parent = new FakeElement();
  renderer.appendStructured(
    parent,
    [
      ['détester', ['first-person plural imperfect indicative']],
      ['détester', ['first-person plural present subjunctive']],
    ],
    'wty-fr-fr',
    {},
    'fr',
  );

  const list = parent.children.find((child) => child.tagName === 'UL');
  assert.ok(list);
  assert.equal(list.children.length, 2);
  assert.equal(renderedText(list.children[0]), 'détester first-person plural imperfect indicative');
  assert.equal(renderedText(list.children[1]), 'détester first-person plural present subjunctive');
});
