import { describe, it, expect } from 'vitest';
import {
  yuanToCents,
  centsToYuan,
  formatCny,
  formatCnySafe,
  normalizeYuan,
  priceInputError,
  multiplyCents,
} from '../src/money.js';

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

describe('输入归一化（块钱的手写习惯）', () => {
  it('省略整数部分的写法', () => {
    expect(normalizeYuan('.5')).toBe('0.5');
    expect(yuanToCents('.5')).toBe(50);
    expect(yuanToCents('.05')).toBe(5);
  });

  it('结尾还挂着小数点（输入到一半）', () => {
    expect(normalizeYuan('12.')).toBe('12');
    expect(yuanToCents('12.')).toBe(1200);
  });

  it('首尾空格不影响换算', () => {
    expect(yuanToCents(' 12.50 ')).toBe(1250);
  });

  it('归一化不"帮倒忙"：非法输入不会被改成合法值', () => {
    expect(normalizeYuan('1.2.3')).toBe('1.2.3');
    expect(normalizeYuan('abc')).toBe('abc');
  });
});

describe('超大金额（超过 2^53 分就不再精确）', () => {
  it('直接报错，不返回一个悄悄错掉的整数', () => {
    expect(() => yuanToCents('99999999999999999')).toThrow(/金额过大/);
  });
});

describe('formatCnySafe（视图层兜底）', () => {
  it('字段缺失 / 脏数据时给占位符，不抛错', () => {
    expect(formatCnySafe(undefined)).toBe('¥--');
    expect(formatCnySafe(null)).toBe('¥--');
    expect(formatCnySafe('')).toBe('¥--');
    expect(formatCnySafe('abc')).toBe('¥--');
  });

  it('正常值仍然走 ¥xx.xx', () => {
    expect(formatCnySafe(1250)).toBe('¥12.50');
    expect(formatCnySafe('1250')).toBe('¥12.50');
    expect(formatCnySafe(0)).toBe('¥0.00');
  });
});

describe('priceInputError（发布 / 下单表单直接用）', () => {
  it('通过时返回空串', () => {
    expect(priceInputError('19.9')).toBe('');
    expect(priceInputError('12.50')).toBe('');
    expect(priceInputError('1')).toBe('');
  });

  it('空值 / 0 / 负数 / 三位小数 / 非数字各有各的提示', () => {
    expect(priceInputError('')).toBe('价格不能为空');
    expect(priceInputError('   ')).toBe('价格不能为空');
    expect(priceInputError('0')).toBe('价格必须大于 0');
    expect(priceInputError('0.00')).toBe('价格必须大于 0');
    expect(priceInputError('-1')).toMatch(/金额格式不正确/);
    expect(priceInputError('19.999')).toMatch(/最多两位小数/);
    expect(priceInputError('abc')).toMatch(/金额格式不正确/);
  });
});

describe('multiplyCents（单价 × 数量 = 总价，L1-8）', () => {
  it('整数分相乘，全程不经过浮点', () => {
    expect(multiplyCents(1999, 2)).toBe(3998);
    expect(multiplyCents(1, 3)).toBe(3);
    expect(multiplyCents(99999, 999)).toBe(99999 * 999);
  });

  it('常见易错值也对得上：19.90 × 3、0.01 × 7', () => {
    expect(formatCny(multiplyCents(yuanToCents('19.9'), 3))).toBe('¥59.70');
    expect(formatCny(multiplyCents(yuanToCents('0.01'), 7))).toBe('¥0.07');
    // 19.9 * 3 用浮点会得到 59.699999999999996 这类尾巴，这里必须是整数分
    expect(multiplyCents(yuanToCents('19.9'), 3)).toBe(5970);
  });

  it('和服务端的算法一致（服务端也是 price * quantity）', () => {
    for (const [price, quantity] of [
      [19900, 3],
      [4990, 2],
      [1, 1],
    ]) {
      expect(multiplyCents(price, quantity)).toBe(price * quantity);
    }
  });

  it('参数不合法时抛错，不返回一个看起来正常的错数', () => {
    expect(() => multiplyCents(1000, 0)).toThrow(/正整数/);
    expect(() => multiplyCents(1000, -1)).toThrow(/正整数/);
    expect(() => multiplyCents(1000, 1.5)).toThrow(/正整数/);
    expect(() => multiplyCents(10.5, 2)).toThrow(/整数分/);
    expect(() => multiplyCents(undefined, 2)).toThrow(/整数分/);
    // Number(null) === 0 / Number('') === 0：不显式挡掉就会算出一个「正常」的 ¥0.00
    expect(() => multiplyCents(null, 2)).toThrow(/整数分/);
    expect(() => multiplyCents('', 2)).toThrow(/整数分/);
  });
});
