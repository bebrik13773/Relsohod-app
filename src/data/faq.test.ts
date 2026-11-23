import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { FAQ_ITEMS } from './faq';

test('FAQ_ITEMS contains unique non-empty questions covering key Relsokhod topics', () => {
  assert.ok(FAQ_ITEMS.length >= 6);
  const ids = new Set<string>();
  for (const item of FAQ_ITEMS) {
    assert.ok(item.id.trim().length > 0);
    assert.ok(item.question.trim().length > 10);
    assert.ok(item.answer.trim().length > 30);
    assert.equal(ids.has(item.id), false, `Duplicate FAQ id: ${item.id}`);
    ids.add(item.id);
  }

  const combined = FAQ_ITEMS.map((i) => `${i.question} ${i.answer}`).join(' ').toLowerCase();
  assert.ok(combined.includes('пересад'));
  assert.ok(combined.includes('геолокац'));
  assert.ok(combined.includes('.relsohod'));
  assert.ok(combined.includes('аудиогид'));
});

test('README.md includes every FAQ question and answer from FAQ_ITEMS', () => {
  const readme = readFileSync(resolve(process.cwd(), 'README.md'), 'utf8');
  for (const item of FAQ_ITEMS) {
    assert.ok(
      readme.includes(item.question),
      `README.md is missing FAQ question: ${item.question}`
    );
    assert.ok(
      readme.includes(item.answer),
      `README.md is missing FAQ answer for: ${item.id}`
    );
  }
});
