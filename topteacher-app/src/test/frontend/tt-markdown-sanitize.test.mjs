import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { sanitize } from '../../../node_modules/hast-util-sanitize/index.js';
import ts from 'typescript';

const source = readFileSync(new URL('../../main/frontend/tt-markdown-sanitize.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 },
});
const { allowClassNames } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

test('merges extension classes into an existing className allow-list', () => {
  const spanAttributes = allowClassNames([
    'ariaHidden',
    ['className', 'ws-markdown-alert-icon', 'ws-markdown-line-number'],
  ], 'tt-criterion', 'tt-criterion-badge');
  const tree = sanitize({
    type: 'root',
    children: [{
      type: 'element',
      tagName: 'span',
      properties: { className: ['tt-criterion', 'tt-criterion-badge', 'ws-markdown-line-number'] },
      children: [{ type: 'text', value: '2' }],
    }],
  }, {
    tagNames: ['span'],
    attributes: { span: spanAttributes },
  });

  assert.deepEqual(tree.children[0].properties.className,
    ['tt-criterion', 'tt-criterion-badge', 'ws-markdown-line-number']);
});

test('preserves an unrestricted className definition', () => {
  assert.deepEqual(allowClassNames(['className', 'dataCriterionKey'], 'tt-criterion'),
    ['className', 'dataCriterionKey']);
});
