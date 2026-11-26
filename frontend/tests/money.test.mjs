import { describe, it, expect } from 'vitest';
import { toFen, formatYuan, estimateTotal } from '../src/money.js';

describe('元 -> 分（覆盖不同量级，全部为整数分）', () => {
  it('整数元：1 元 = 100 分', () => {
    expect(toFen('1')).toBe(100);
  });

  it('一位小数：12.5 元 = 1250 分', () => {
    expect(toFen('12.5')).toBe(1250);
  });

  it('两位小数：19.99 元 = 1999 分（避开 19.99*100 的浮点误差）', () => {
    expect(toFen('19.99')).toBe(1999);
  });

  it('极小金额：0.01 元 = 1 分', () => {
    expect(toFen('0.01')).toBe(1);
  });

  it('大额：199999.99 元 = 19999999 分，不丢精度', () => {
    expect(toFen('199999.99')).toBe(19999999);
  });

  it('超过两位小数被拦下（19.999）', () => {
    expect(() => toFen('19.999')).toThrow();
  });

  it('0 与非正数被拦下', () => {
    expect(() => toFen('0')).toThrow();
  });
});

describe('分 -> ¥xx.xx（整数拆分，无浮点尾巴）', () => {
  it('普通金额：1999 分 = ¥19.99', () => {
    expect(formatYuan(1999)).toBe('¥19.99');
  });

  it('整数元补两位零：100 分 = ¥1.00', () => {
    expect(formatYuan(100)).toBe('¥1.00');
  });

  it('大额：9999999 分 = ¥99999.99', () => {
    expect(formatYuan(9999999)).toBe('¥99999.99');
  });

  it('极小金额：1 分 = ¥0.01', () => {
    expect(formatYuan(1)).toBe('¥0.01');
  });
});

describe('下单预估总价', () => {
  it('单价 1999 分 × 3 件 = 5997 分，与服务端 totalAmount 口径一致', () => {
    expect(estimateTotal(1999, 3)).toBe(5997);
  });
});
