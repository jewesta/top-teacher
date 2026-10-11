import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../../main/frontend/tt-material-image-reference.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 },
});
const { parseMaterialImageTarget } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

test('parses width and alignment independently with a centered default', () => {
  assert.deepEqual(parseMaterialImageTarget('img:abcdefgh'), {
    id: 'abcdefgh', alignment: 'center',
  });
  assert.deepEqual(parseMaterialImageTarget('img:abcdefgh?align=center'), {
    id: 'abcdefgh', alignment: 'center',
  });
  assert.deepEqual(parseMaterialImageTarget('img:abcdefgh?align=right&width=25'), {
    id: 'abcdefgh', width: '25', alignment: 'right',
  });
});

test('rejects malformed, unknown, duplicate and differently cased parameters', () => {
  for (const target of ['img:abcdefgh?', 'img:abcdefgh?align=middle', 'img:abcdefgh?align=CENTER',
    'img:abcdefgh?align=right&align=center', 'img:abcdefgh?width=25&width=50',
    'img:abcdefgh?width=0', 'img:abcdefgh?extra=1', 'img:ABCDefgh?align=center']) {
    assert.equal(parseMaterialImageTarget(target), undefined, target);
  }
});
