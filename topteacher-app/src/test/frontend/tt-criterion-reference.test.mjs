import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../../main/frontend/tt-criterion-reference.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023 },
});
const { CRITERION_NAMESPACE, criterionTarget, parseCriterionTarget } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

test('uses the criterion scheme and omits the one-point default', () => {
  assert.equal(CRITERION_NAMESPACE, 'crit');
  assert.equal(criterionTarget('2', '1', false), '2');
  assert.equal(criterionTarget('2', '1.0', false), '2');
  assert.equal(criterionTarget('2', '1,0', false), '2');
  assert.equal(criterionTarget('2', '0,5', false), '2?points=0,5');
  assert.equal(criterionTarget('2', '4.5', true), '2?points=4.5');
});

test('keeps the custom placeholder selectable and preserves the stable key', () => {
  const target = criterionTarget('12', '?', true);
  const reference = parseCriterionTarget(target);
  assert.equal(target, '12?points=?');
  assert.equal(reference.key, '12');
  assert.equal(reference.pointValue, '?');
  assert.equal(reference.validParameters, true);
  assert.equal(target.slice(reference.valueRange.start, reference.valueRange.end), '?');
  assert.equal(criterionTarget(reference.key, '8,5', true), '12?points=8,5');
});

test('parses default and explicit point values without including the query in the key', () => {
  assert.deepEqual(parseCriterionTarget('2'), { key: '2', validParameters: true });
  for (const points of ['1', '0,5', '1.5', '999', '']) {
    const target = `2?points=${points}`;
    const reference = parseCriterionTarget(target);
    assert.equal(reference.key, '2');
    assert.equal(reference.pointValue, points);
    assert.equal(reference.validParameters, true);
    assert.equal(target.slice(reference.valueRange.start, reference.valueRange.end), points);
  }
});

test('decodes query values but retains their original source range for editing', () => {
  const target = '12?%70oints=0%2C5';
  const reference = parseCriterionTarget(target);
  assert.equal(reference.key, '12');
  assert.equal(reference.pointValue, '0,5');
  assert.equal(reference.validParameters, true);
  assert.equal(target.slice(reference.valueRange.start, reference.valueRange.end), '0%2C5');
});

test('rejects unknown, duplicate, incomplete, fragmented and malformed parameters', () => {
  for (const query of ['', 'points', 'width=75', 'points=2&points=3', 'points=2&width=75',
    'points=2&', 'points=2#fragment', 'points=%', 'points=%GG', 'Points=2']) {
    const reference = parseCriterionTarget(`2?${query}`);
    assert.equal(reference.key, '2');
    assert.equal(reference.validParameters, false, query);
    assert.equal(reference.valueRange, undefined);
  }
});
