import assert from 'node:assert/strict';
import test from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { loadFrontendModule } from './load-frontend-module.mjs';

const { markdownPreviewConfiguration } = loadFrontendModule(new URL(
  '../../../../westarps-vaadin-markdown/src/main/resources/META-INF/resources/frontend/de/westarps/vaadin/markdown/ws-markdown-support.tsx',
  import.meta.url,
));
const { options } = markdownPreviewConfiguration([], { state: {}, emit: () => {} });

for (const count of [9, 10, 27, 99, 100]) {
  test(`the shared preview reserves all ${String(count).length} marker digits for ${count} footnotes`, () => {
    const ids = Array.from({ length: count }, (_, index) => index + 1);
    const markdown = ids.map((id) => `Text[^note${id}]`).join(' ') + '\n\n'
      + ids.map((id) => `[^note${id}]: Fußnote ${id}`).join('\n');
    const html = renderToStaticMarkup(React.createElement(ReactMarkdown, {
      remarkPlugins: [remarkGfm, ...options.remarkPlugins],
      rehypePlugins: options.rehypePlugins,
      remarkRehypeOptions: options.remarkRehypeOptions,
      components: options.components,
      children: markdown,
    }));
    assert.match(html, new RegExp(`--ws-footnote-marker-digits:${String(count).length}`));
    assert.equal([...html.matchAll(/<li id="[^"]*fn-note/g)].length, count);
    assert.match(html, new RegExp(`<sup><span>${count}</span></sup>`));
    assert.match(html, new RegExp(`Fußnote ${count}`));
  });
}

test('ordinary sections retain their content and styles without footnote indentation metadata', () => {
  const element = options.components.section({
    children: 'Normaler Inhalt', style: { color: 'red' },
  });
  const html = renderToStaticMarkup(element);
  assert.equal(html, '<section style="color:red">Normaler Inhalt</section>');
});
