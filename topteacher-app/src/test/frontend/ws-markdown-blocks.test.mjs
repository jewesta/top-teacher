import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';
import { unified } from 'unified';
import remarkParse from 'remark-parse';
import remarkGfm from 'remark-gfm';
import { remarkAlert } from 'remark-github-blockquote-alert';
import remarkRehype from 'remark-rehype';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';

const source = readFileSync(new URL(
  '../../../../westarps-vaadin-markdown/src/main/resources/META-INF/resources/frontend/de/westarps/vaadin/markdown/ws-markdown-blocks.ts',
  import.meta.url,
), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 },
});
const { remarkMarkdownBlocks, markdownBlockPluginsFirst, markdownBlockSanitizeSchema, overflowingNumberedLines } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

async function render(source) {
  const processor = unified().use(remarkParse)
    .use(markdownBlockPluginsFirst('remark', [remarkAlert, remarkMarkdownBlocks, remarkGfm]))
    .use(remarkRehype).use(rehypeSanitize, markdownBlockSanitizeSchema(defaultSchema));
  return processor.run(processor.parse(source), { value: source });
}

function nodes(tree, predicate) {
  return [ ...(predicate(tree) ? [tree] : []), ...(tree.children ?? []).flatMap((child) => nodes(child, predicate)) ];
}

function text(node) {
  return node.value ?? (node.children ?? []).map(text).join('');
}

test('the bundled MCP guide uses examples accepted by the actual alert/numbered/footnote renderer', async () => {
  const guide = readFileSync(new URL(
    '../../../../topteacher-mcp/src/main/resources/mcp/markdown-guide.md', import.meta.url,
  ), 'utf8');
  const examples = [...guide.matchAll(/```markdown\n([\s\S]*?)```/g)].map((match) => match[1]);
  const numbered = examples.find((example) => example.includes('[!NUMBERED]'));
  assert.ok(numbered, 'the discoverable guide must include a complete numbered source example');
  const numberedTree = await render(numbered);
  const rows = nodes(numberedTree, (node) => node.properties?.className?.includes('ws-markdown-numbered-line'));
  assert.equal(rows.length, 3);
  assert.deepEqual(rows.map((row) => row.children[1].properties.dataLineNumber), [1, undefined, 2]);
  assert.equal(nodes(numberedTree, (node) => node.properties?.dataFootnotes).length, 1);
  assert.equal(nodes(rows[0], (node) => node.properties?.dataFootnoteRef).length, 1);
  const alerts = examples.find((example) => example.includes('[!TIP]'));
  const alertTree = await render(alerts);
  assert.equal(nodes(alertTree, (node) => node.properties?.className?.includes('ws-markdown-alert')).length, 2);
});

test('renders all GitHub alerts with German labels and retains inline formatting', async () => {
  const tree = await render(['NOTE', 'TIP', 'IMPORTANT', 'WARNING', 'CAUTION']
    .map((type) => `> [!${type}]\n> **Text** mit [Link](https://example.com).`).join('\n\n'));
  const alerts = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-alert'));
  assert.equal(alerts.length, 5);
  assert.deepEqual(alerts.map((node) => text(nodes(node,
    (child) => child.properties?.className?.includes('ws-markdown-alert-title'))[0])),
  ['Hinweis', 'Tipp', 'Wichtig', 'Warnung', 'Vorsicht']);
  const icons = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-alert-icon'));
  assert.equal(icons.length, 5);
  assert.ok(icons.every((icon) => text(icon) === '' && icon.properties.ariaHidden === 'true'));
  assert.equal(nodes(tree, (node) => node.tagName === 'strong').length, 5);
  assert.equal(nodes(tree, (node) => node.tagName === 'a').length, 5);
  assert.ok(!text(tree).includes('[!'));
});

test('uses all five bundled SVG resources for preview masks', () => {
  const css = readFileSync(new URL(
    '../../../../westarps-vaadin-markdown/src/main/resources/META-INF/resources/frontend/de/westarps/vaadin/markdown/ws-markdown-blocks.css',
    import.meta.url,
  ), 'utf8');
  const paths = [];
  for (const type of ['note', 'tip', 'important', 'warning', 'caution']) {
    assert.ok(css.includes(`url('./icons/${type}.svg')`));
    const svg = readFileSync(new URL(`../../../../assets/markdown-alerts/${type}.svg`, import.meta.url), 'utf8');
    assert.ok(svg.includes('xmlns="http://www.w3.org/2000/svg"'));
    assert.ok(!/<(?:script|image|foreignObject)\b|\bhref=/.test(svg));
    paths.push(svg.match(/<path d="([^"]+)"/)[1]);
  }
  assert.equal(new Set(paths).size, 5);
});

test('leaves ordinary quotes, unknown markers and code samples alone', async () => {
  const tree = await render('> Normaler Text\n\n> [!UNKNOWN]\n> Text\n\n```markdown\n> [!TIP]\n> Beispiel\n```');
  assert.equal(nodes(tree, (node) => node.tagName === 'blockquote').length, 2);
  assert.equal(nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-alert')).length, 0);
  assert.ok(text(tree).includes('[!UNKNOWN]'));
  assert.ok(text(tree).includes('[!TIP]'));
});

test('preserves blank spacing without counting it and retains links and footnotes', async () => {
  const tree = await render('> [!NUMBERED]\n> Erste **Zeile**.[^1]\n>\n> Zweite [Zeile][quelle]\n\n'
    + '[^1]: Eine Fußnote.\n\n[quelle]: https://example.com');
  const rows = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-numbered-line'));
  assert.equal(rows.length, 3);
  assert.deepEqual(rows.map((row) => text(row.children[0])), ['1', '', '']);
  assert.deepEqual(rows.map((row) => row.children[1].properties.dataLineNumber), [1, undefined, 2]);
  assert.equal(text(rows[1].children[1]), '');
  assert.equal(nodes(rows[0], (node) => node.properties?.dataFootnoteRef).length, 1);
  assert.equal(nodes(rows[2], (node) => node.tagName === 'a')[0].properties.href, 'https://example.com');
  assert.equal(nodes(tree, (node) => node.properties?.dataFootnotes).length, 1);
});

test('labels the first and every fifth non-empty line and restarts each block at one', async () => {
  const block = (count) => '> [!NUMBERED]\n' + Array.from({ length: count }, (_, index) =>
    `${index === 4 ? '>\n' : ''}> Zeile ${index + 1}`).join('\n');
  const tree = await render(`${block(16)}\n\n${block(6)}`);
  const blocks = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-numbered'));
  assert.equal(blocks.length, 2);
  for (const [index, block] of blocks.entries()) {
    const rows = nodes(block, (node) => node.properties?.className?.includes('ws-markdown-numbered-line'));
    assert.equal(rows.length, index === 0 ? 17 : 7);
    assert.deepEqual(rows.flatMap((row, index) => text(row.children[0]) ? [[index + 1, text(row.children[0])]] : []),
      index === 0 ? [[1, '1'], [6, '5'], [11, '10'], [16, '15']] : [[1, '1'], [6, '5']]);
    assert.deepEqual(rows.flatMap((row) => row.children[1].properties.dataLineNumber ?? []),
      Array.from({ length: index === 0 ? 16 : 6 }, (_, line) => line + 1));
    assert.equal(text(rows[4].children[1]), '');
    assert.equal(text(rows[4].children[0]), '');
    assert.equal(text(rows.at(-1).children[1]), `Zeile ${rows.length - 1}`);
  }
});

test('skips leading, consecutive, trailing and whitespace-only blank lines', async () => {
  const tree = await render('> [!NUMBERED]\n>\n>   \n> \t\n> Erste Zeile\n>\n> \t \n> Zweite Zeile\n>\n>   ');
  const rows = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-numbered-line'));
  assert.equal(rows.length, 9);
  assert.deepEqual(rows.map((row) => row.children[1].properties.dataLineNumber),
    [undefined, undefined, undefined, 1, undefined, undefined, 2, undefined, undefined]);
  assert.deepEqual(rows.map((row) => text(row.children[0])), ['', '', '', '1', '', '', '', '', '']);
});

test('counts sixteen verse lines without counting their three stanza separators', async () => {
  const stanzas = Array.from({ length: 4 }, (_, stanza) => Array.from({ length: 4 }, (_, line) =>
    `> Strophe ${stanza + 1}, Zeile ${line + 1}`).join('\n'));
  const tree = await render(`> [!NUMBERED]\n${stanzas.join('\n>\n')}`);
  const rows = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-numbered-line'));
  assert.equal(rows.length, 19);
  assert.deepEqual(rows.flatMap((row, index) => text(row.children[0]) ? [[index + 1, text(row.children[0])]] : []),
    [[1, '1'], [6, '5'], [12, '10'], [18, '15']]);
  for (const separator of [4, 9, 14]) {
    assert.equal(text(rows[separator].children[0]), '');
    assert.equal(rows[separator].children[1].properties.dataLineNumber, undefined);
  }
});

test('reports clipping using counted line numbers, including unlabelled lines after blanks', async () => {
  const tree = await render('> [!NUMBERED]\n>\n> Erste Zeile\n>\n> Zweite Zeile\n> Dritte Zeile\n> Vierte Zeile\n>\n> Fünfte Zeile');
  const rows = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-numbered-line'));
  const measured = rows.map((row) => {
    const lineNumber = row.children[1].properties.dataLineNumber;
    return {
      dataset: lineNumber === undefined ? {} : { lineNumber: String(lineNumber) },
      scrollWidth: lineNumber === 1 ? 101 : 120,
      clientWidth: 100,
    };
  });
  assert.deepEqual(overflowingNumberedLines(measured), [2, 3, 4, 5]);
});

test('renders line numbers as normal text with the source line font size and line height', async () => {
  const tree = await render('> [!NUMBERED]\n> Erste Zeile');
  const number = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-line-number'))[0];
  assert.equal(number.tagName, 'span');
  assert.equal(nodes(number, (node) => node.tagName === 'sup').length, 0);
  const css = readFileSync(new URL(
    '../../../../westarps-vaadin-markdown/src/main/resources/META-INF/resources/frontend/de/westarps/vaadin/markdown/ws-markdown-blocks.css',
    import.meta.url,
  ), 'utf8');
  const rule = css.match(/\.wmde-markdown \.ws-markdown-line-number\s*\{([^}]*)\}/)[1];
  assert.match(rule, /font-size:\s*inherit;/);
  assert.match(rule, /line-height:\s*inherit;/);
});

test('keeps a long source line as one row and does not interpret it as a list', async () => {
  const content = `1. ${'Langer Text '.repeat(40)}`;
  const tree = await render(`> [!NUMBERED]\n> ${content}\n> Letzte Zeile`);
  const rows = nodes(tree, (node) => node.properties?.className?.includes('ws-markdown-numbered-line'));
  assert.equal(rows.length, 2);
  assert.equal(text(rows[0].children[1]), content.trimEnd());
  assert.equal(nodes(tree, (node) => node.tagName === 'ol').length, 0);
});

test('does not enable raw HTML execution inside alerts', async () => {
  const tree = await render('> [!TIP]\n> Sicher <img src=x onerror=alert(1)> <script>alert(2)</script>');
  assert.equal(nodes(tree, (node) => node.tagName === 'script').length, 0);
  assert.equal(nodes(tree, (node) => node.properties?.onerror).length, 0);
});
