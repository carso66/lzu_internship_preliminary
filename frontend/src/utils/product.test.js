import { describe, expect, it } from 'vitest'
import { normalizeProductTitle } from './product.js'

const LONG_TITLE =
  '九成新英语听力耳机二手转让含原装收纳盒与两副耳帽可小刀兰州大学本部自提优先诚心要的联系请备注宿舍楼号'

describe('normalizeProductTitle', () => {
  it('移除混入标题的 script 内容，而不是只依赖文本插值保证安全', () => {
    expect(normalizeProductTitle("<script>alert('xss')</script> 二手教材")).toBe(
      '二手教材',
    )
  })

  it('移除 HTML 标签、控制字符和零宽字符', () => {
    expect(normalizeProductTitle('<b>校园</b> 卫衣\u200B')).toBe('校园 卫衣')
    expect(normalizeProductTitle('\u0000二手\uFEFF教材')).toBe('二手教材')
  })

  it('保留正常长标题，交给 CSS 做单行省略', () => {
    expect(normalizeProductTitle(LONG_TITLE)).toBe(LONG_TITLE)
  })

  it('空标题和非字符串使用兜底文案', () => {
    expect(normalizeProductTitle('   ', '未命名商品')).toBe('未命名商品')
    expect(normalizeProductTitle(null, '未命名商品')).toBe('未命名商品')
    expect(normalizeProductTitle(undefined, '未命名商品')).toBe('未命名商品')
  })
})