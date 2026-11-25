import { describe, it, expect } from 'vitest';
import { yuanToCents, centsToYuan, formatCny } from '../src/money.js';

describe('yuanToCents（元 -> 分）', () => {
  it('整数元', () => {
    expect(yuanToCents('12')).toBe(1200);
    expect(yuanToCents('199')).toBe(19900);
  });

  it('一位小数', () => {
    expect(yuanToCents('19.9')).toBe(1990);
    expect(yuanToCents('0.5')).toBe(50);
  });

  it('两位小数', () => {
    expect(yuanToCents('12.50')).toBe(1250);
    expect(yuanToCents('0.07')).toBe(7);
  });

  it('极小金额', () => {
    expect(yuanToCents('0.01')).toBe(1);
    expect(yuanToCents('0.1')).toBe(10);
  });

  it('非法输入抛错', () => {
    expect(() => yuanToCents('19.999')).toThrow();
    expect(() => yuanToCents('')).toThrow();
    expect(() => yuanToCents('abc')).toThrow();
    expect(() => yuanToCents('-1')).toThrow();
  });
});

describe('centsToYuan / formatCny（分 -> 元）', () => {
  it('固定两位小数', () => {
    expect(centsToYuan(1990)).toBe('19.90');
    expect(centsToYuan(1)).toBe('0.01');
    expect(centsToYuan(9999999)).toBe('99999.99');
  });

  it('带 ¥ 的展示格式', () => {
    expect(formatCny(1250)).toBe('¥12.50');
    expect(formatCny(100)).toBe('¥1.00');
  });
});
