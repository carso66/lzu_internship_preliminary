import { describe, expect, it } from 'vitest'
import { getQuantityError } from './quantity.js'

describe('getQuantityError', () => {
  it('接受 1 到库存之间的整数，兼容数字字符串', () => {
    expect(getQuantityError(1, 5)).toBe('')
    expect(getQuantityError('3', 5)).toBe('')
    expect(getQuantityError(5, 5)).toBe('')
    expect(getQuantityError(' 3 ', ' 5 ')).toBe('')
  })

  it('拒绝零、负数、小数和非数字', () => {
    expect(getQuantityError(0, 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError(-1, 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError(1.5, 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError('abc', 5)).toBe('购买数量必须是正整数')
  })

  it('拒绝会被 Number 误判为数字的非数量输入', () => {
    expect(getQuantityError(true, 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError(null, 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError(undefined, 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError([], 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError([1], 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError({}, 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError('1e2', 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError('0x10', 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError('1.0', 5)).toBe('购买数量必须是正整数')
    expect(getQuantityError(Number.MAX_SAFE_INTEGER + 1, 5)).toBe(
      '购买数量必须是正整数',
    )
  })

  it('拒绝超过库存的数量并给出明确上限', () => {
    expect(getQuantityError(6, 5)).toBe('购买数量不能超过库存（5 件）')
  })

  it('库存为 0 时任何正数都不可购买', () => {
    expect(getQuantityError(1, 0)).toBe('购买数量不能超过库存（0 件）')
    expect(getQuantityError(1, '0')).toBe('购买数量不能超过库存（0 件）')
  })

  it('库存数据异常时拒绝提交', () => {
    expect(getQuantityError(1, undefined)).toBe('商品库存信息异常')
    expect(getQuantityError(1, null)).toBe('商品库存信息异常')
    expect(getQuantityError(1, '')).toBe('商品库存信息异常')
    expect(getQuantityError(1, '  ')).toBe('商品库存信息异常')
    expect(getQuantityError(1, -1)).toBe('商品库存信息异常')
    expect(getQuantityError(1, Number.NaN)).toBe('商品库存信息异常')
    expect(getQuantityError(1, Number.POSITIVE_INFINITY)).toBe(
      '商品库存信息异常',
    )
    expect(getQuantityError(1, true)).toBe('商品库存信息异常')
  })

  it('库存为小数时向下取整后按整数库存判断', () => {
    expect(getQuantityError(5, 5.9)).toBe('')
    expect(getQuantityError(6, 5.9)).toBe('购买数量不能超过库存（5 件）')
    expect(getQuantityError(1, '0.9')).toBe('购买数量不能超过库存（0 件）')
  })
})
