import { describe, expect, it } from 'vitest'
import { fenToYuan, formatPrice, yuanToFen } from './money.js'

describe('yuanToFen', () => {
  it.each([
    ['0.01', 1],
    ['19.9', 1990],
    ['12.5', 1250],
    ['1', 100],
    ['100', 10000],
  ])('把 %s 元转换为 %i 分', (yuan, expected) => {
    const fen = yuanToFen(yuan)

    expect(fen).toBe(expected)
    expect(Number.isInteger(fen)).toBe(true)
  })

  it('修正浮点乘法误差，结果始终是整数分', () => {
    expect(yuanToFen('19.99')).toBe(1999)
    expect(yuanToFen(0.07)).toBe(7)
    expect(Number.isInteger(yuanToFen('0.29'))).toBe(true)
  })

  it('支持首尾空白，但拒绝空值和非法金额', () => {
    expect(yuanToFen(' 19.90 ')).toBe(1990)
    expect(() => yuanToFen('')).toThrow('金额不能为空')
    expect(() => yuanToFen('   ')).toThrow('金额不能为空')
    expect(() => yuanToFen('abc')).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
    expect(() => yuanToFen(Number.POSITIVE_INFINITY)).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
  })

  it('拒绝零和负数，不允许出现负金额', () => {
    expect(() => yuanToFen(0)).toThrow('金额必须大于 0')
    expect(() => yuanToFen('0.00')).toThrow('金额必须大于 0')
    expect(() => yuanToFen(-1)).toThrow('金额必须是大于 0 的数字，最多两位小数')
    expect(() => yuanToFen('-0.01')).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
  })

  it('拒绝超过两位小数、科学计数法和千分位', () => {
    expect(() => yuanToFen('0.001')).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
    expect(() => yuanToFen('1.999')).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
    expect(() => yuanToFen('1e3')).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
    expect(() => yuanToFen('1,000')).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
  })

  it('拒绝会被 String 误当成数字的非金额输入', () => {
    expect(() => yuanToFen(true)).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
    expect(() => yuanToFen(null)).toThrow('金额不能为空')
    expect(() => yuanToFen(undefined)).toThrow('金额不能为空')
    expect(() => yuanToFen([19.9])).toThrow(
      '金额必须是大于 0 的数字，最多两位小数',
    )
  })

  it('拒绝超过安全整数范围的金额，避免失真', () => {
    expect(() => yuanToFen('100000000000000000000')).toThrow(
      '金额超出可处理范围',
    )
  })
})

describe('fenToYuan / formatPrice', () => {
  it('分转元固定保留两位小数', () => {
    expect(fenToYuan(0)).toBe('0.00')
    expect(fenToYuan(1)).toBe('0.01')
    expect(fenToYuan(199)).toBe('1.99')
    expect(fenToYuan(1990)).toBe('19.90')
  })

  it('0 分显示为 ¥0.00，不出现 NaN', () => {
    expect(formatPrice(0)).toBe('¥0.00')
    expect(formatPrice('0')).toBe('¥0.00')
  })

  it('按约定格式展示整数分金额', () => {
    expect(formatPrice(199)).toBe('¥1.99')
    expect(formatPrice(19900)).toBe('¥199.00')
    expect(formatPrice(9999999)).toBe('¥99999.99')
  })

  it('对脏金额数据展示兜底值而不是 NaN 或负价格', () => {
    expect(fenToYuan(undefined)).toBe('0.00')
    expect(fenToYuan(null)).toBe('0.00')
    expect(fenToYuan(-1)).toBe('0.00')
    expect(formatPrice('not-a-number')).toBe('¥0.00')
    expect(formatPrice(Number.NaN)).toBe('¥0.00')
    expect(formatPrice(-199)).toBe('¥0.00')
  })
})
