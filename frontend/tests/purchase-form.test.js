import { describe, it, expect } from 'vitest';
import { estimateTotal, quantityInputError } from '../src/purchase-form.js';
import { formatCny } from '../src/money.js';

describe('购买数量校验（L1-6）', () => {
  it('正整数都通过', () => {
    for (const value of ['1', '2', '10', '999']) {
      expect(quantityInputError(value)).toBe('');
    }
  });

  it('空 / 0 / 负数 / 小数 / 非数字都被拦下，且各有提示', () => {
    expect(quantityInputError('')).toBe('购买数量不能为空');
    expect(quantityInputError('   ')).toBe('购买数量不能为空');
    expect(quantityInputError('0')).toBe('购买数量必须大于 0');
    expect(quantityInputError('-1')).toBe('购买数量必须是正整数');
    expect(quantityInputError('1.5')).toBe('购买数量必须是正整数');
    expect(quantityInputError('2a')).toBe('购买数量必须是正整数');
  });
});

describe('前端预估总价（L1-8：必须等于服务端 totalAmount）', () => {
  it('单价（分）× 数量，结果还是整数分', () => {
    expect(estimateTotal(1999, '2')).toBe(3998);
    expect(estimateTotal(1999, 2)).toBe(3998);
    expect(formatCny(estimateTotal(1999, '2'))).toBe('¥39.98');
  });

  it('多件 / 极小金额 / 大额都对得上', () => {
    expect(estimateTotal(4990, '3')).toBe(4990 * 3);
    expect(estimateTotal(1, '7')).toBe(7);
    expect(formatCny(estimateTotal(1, '7'))).toBe('¥0.07');
    expect(estimateTotal(19900, '12')).toBe(19900 * 12);
  });

  it('数量非法时返回 null（界面显示「—」，不会出现 NaN）', () => {
    expect(estimateTotal(1999, '')).toBeNull();
    expect(estimateTotal(1999, '0')).toBeNull();
    expect(estimateTotal(1999, 'abc')).toBeNull();
  });

  it('单价字段缺失时返回 null 而不是抛错（一条坏数据不该让弹窗崩掉）', () => {
    expect(estimateTotal(undefined, '2')).toBeNull();
    expect(estimateTotal(null, '2')).toBeNull();
  });
});
