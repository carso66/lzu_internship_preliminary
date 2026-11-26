import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { yuanToCents } from '../src/money.mjs';

describe('yuanToCents', () => {
  for (const [input, expected] of [
    ['12', 1200],
    ['19.9', 1990],
    ['12.50', 1250],
    ['0.01', 1],
    ['99999.99', 9999999],
  ]) {
    it(`converts ${input} yuan to ${expected} cents`, () => {
      assert.equal(yuanToCents(input), expected);
    });
  }

  for (const input of ['', '0', '-1', '19.999']) {
    it(`rejects invalid price ${JSON.stringify(input)}`, () => {
      assert.throws(() => yuanToCents(input));
    });
  }
});
