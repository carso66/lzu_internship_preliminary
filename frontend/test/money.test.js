import { describe, expect, it } from 'vitest'
import { fenToYuan, formatPrice, multiplyFen, yuanToFen, yuanToPositiveFen } from '../src/utils/money.js'

describe('fenToYuan / formatPrice（分 → 元）', () => {
  it('补足两位小数，末尾的 0 不能省', () => {
    expect(fenToYuan(199)).toBe('1.99')
    expect(fenToYuan(1990)).toBe('19.90')
    expect(fenToYuan(1250)).toBe('12.50')
    expect(fenToYuan(1)).toBe('0.01')
    expect(fenToYuan(0)).toBe('0.00')
    expect(fenToYuan(100)).toBe('1.00')
  })

  it('大金额不丢精度', () => {
    // 种子数据 id=14 就是 9999999 分
    expect(formatPrice(9999999)).toBe('¥99999.99')
    expect(formatPrice(123456789)).toBe('¥1234567.89')
  })

  it('一律带 ¥', () => {
    expect(formatPrice(4590)).toBe('¥45.90')
    expect(formatPrice(0)).toBe('¥0.00')
  })

  it('非法值兜底成 0.00，界面不出现 NaN', () => {
    expect(formatPrice(undefined)).toBe('¥0.00')
    expect(formatPrice(null)).toBe('¥0.00')
    expect(formatPrice('abc')).toBe('¥0.00')
    expect(formatPrice(NaN)).toBe('¥0.00')
  })

  it('字符串数字也能吃（后端偶尔给字符串）', () => {
    expect(formatPrice('1990')).toBe('¥19.90')
  })
})

describe('yuanToFen（元 → 分）', () => {
  it('一位小数：19.9 → 1990，不能出现 1989.9999999999998', () => {
    expect(yuanToFen('19.9')).toBe(1990)
    expect(yuanToFen('0.1')).toBe(10)
    expect(yuanToFen('1.1')).toBe(110)
    expect(yuanToFen('2.3')).toBe(230)
    expect(yuanToFen('8.7')).toBe(870)
  })

  it('两位小数原样换算', () => {
    expect(yuanToFen('12.50')).toBe(1250)
    expect(yuanToFen('0.01')).toBe(1)
    expect(yuanToFen('99.99')).toBe(9999)
  })

  it('整数元', () => {
    expect(yuanToFen('12')).toBe(1200)
    expect(yuanToFen('0')).toBe(0)
    expect(yuanToFen('199')).toBe(19900)
  })

  it('允许 .5 这种省略整数位的写法', () => {
    expect(yuanToFen('.5')).toBe(50)
    expect(yuanToFen('.05')).toBe(5)
  })

  it('三位小数、负数、字母、空值一律返回 null（由调用方给提示）', () => {
    expect(yuanToFen('19.999')).toBeNull()
    expect(yuanToFen('-1')).toBeNull()
    expect(yuanToFen('abc')).toBeNull()
    expect(yuanToFen('')).toBeNull()
    expect(yuanToFen('   ')).toBeNull()
    expect(yuanToFen('1e3')).toBeNull()
    expect(yuanToFen('12.')).toBeNull()
  })

  it('前后空格宽容处理', () => {
    expect(yuanToFen('  12.50  ')).toBe(1250)
  })

  it('数字入参也能用', () => {
    expect(yuanToFen(12.5)).toBe(1250)
  })

  it('超出安全整数范围返回 null，不产生错误金额', () => {
    expect(yuanToFen('99999999999999999')).toBeNull()
  })
})

describe('yuanToPositiveFen', () => {
  it('必须大于 0', () => {
    expect(yuanToPositiveFen('12.50')).toBe(1250)
    expect(yuanToPositiveFen('0.01')).toBe(1)
    expect(yuanToPositiveFen('0')).toBeNull()
    expect(yuanToPositiveFen('0.00')).toBeNull()
  })

  it('非法输入同样是 null', () => {
    expect(yuanToPositiveFen('19.999')).toBeNull()
  })
})

describe('往返换算（元 → 分 → 元）', () => {
  it('常见金额来回一致', () => {
    for (const yuan of ['0.01', '1.99', '12.50', '19.90', '99999.99', '1234567.89']) {
      expect(fenToYuan(yuanToFen(yuan))).toBe(yuan)
    }
  })
})

describe('multiplyFen（单价 × 数量，L1-8 前端预估总价）', () => {
  it('常规整数相乘', () => {
    expect(multiplyFen(2990, 2)).toBe(5980)
    expect(multiplyFen(1, 1)).toBe(1)
  })

  it('数量为 0 时总价是 0，不是 null（商品可以是 0 件，只是不让提交）', () => {
    expect(multiplyFen(2990, 0)).toBe(0)
  })

  it('免费商品（单价 0）也算得出来', () => {
    expect(multiplyFen(0, 5)).toBe(0)
  })

  it('大单价 × 大数量仍精确，不出现浮点误差', () => {
    expect(multiplyFen(99999999, 100)).toBe(9999999900)
  })

  it('结果超出安全整数范围返回 null，不产生错误金额', () => {
    expect(multiplyFen(Number.MAX_SAFE_INTEGER, 2)).toBeNull()
  })

  it('非法入参返回 null，界面据此显示「—」而不是 NaN', () => {
    expect(multiplyFen(null, 1)).toBeNull()
    expect(multiplyFen(undefined, 1)).toBeNull()
    expect(multiplyFen('abc', 2)).toBeNull()
    expect(multiplyFen(2990, 'abc')).toBeNull()
    // 数量必须是整数件：1.5 件不合法
    expect(multiplyFen(2990, 1.5)).toBeNull()
    expect(multiplyFen(NaN, 1)).toBeNull()
  })

  it('字符串数字也吃得下（和 fenToYuan 的宽容度保持一致）', () => {
    expect(multiplyFen('2990', '2')).toBe(5980)
  })

  it('负单价 / 负数量返回 null', () => {
    expect(multiplyFen(-1, 2)).toBeNull()
    expect(multiplyFen(100, -2)).toBeNull()
  })
})
