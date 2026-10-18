import assert from 'node:assert/strict';
import test from 'node:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { loadFrontendModule } from './load-frontend-module.mjs';

let extension;
loadFrontendModule('tt-criterion-markdown.tsx', {
  '@vaadin/react-components/Popover.js': { Popover: () => null },
  '@vaadin/flow-frontend/de/westarps/vaadin/markdown/ws-markdown-extensions': {
    registerMarkdownExtension: (_id, value) => { extension = value; },
  },
});

function panel(text, start = text.length, end = start) {
  return extension.editorCommands()[0].children({
    textApi: { textArea: { value: text, selectionStart: start, selectionEnd: end } },
    close: () => {},
  });
}

function pointButtons(markup) {
  return [...markup.matchAll(/<button[^>]*>/g)].map(([button]) => button);
}

test('opening the criterion selector on empty text renders disabled options without crashing', () => {
  const markup = renderToStaticMarkup(panel(''));
  assert.match(markup, /Text markieren/);
  assert.equal(pointButtons(markup).length, 9);
  assert.ok(pointButtons(markup).every((button) => button.includes('disabled=""')));
});

test('whitespace remains inapplicable and entering a word enables point options', () => {
  const whitespace = renderToStaticMarkup(panel('   '));
  assert.ok(pointButtons(whitespace).every((button) => button.includes('disabled=""')));
  for (const args of [['Begriff'], ['Zwei Begriffe', 0, 13]]) {
    const markup = renderToStaticMarkup(panel(...args));
    assert.doesNotMatch(markup, /Text markieren/);
    assert.ok(pointButtons(markup).every((button) => !button.includes('disabled=""')));
  }
});
